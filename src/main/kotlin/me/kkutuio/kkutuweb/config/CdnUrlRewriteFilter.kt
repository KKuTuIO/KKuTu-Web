/*
 * KKuTu-Web (https://github.com/KKuTuIO/KKuTu-Web)
 * Copyright (C) 2021 KKuTuIO <admin@kkutu.io>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.kkutuio.kkutuweb.config

import jakarta.servlet.DispatcherType
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletOutputStream
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.WriteListener
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.URI
import java.nio.charset.Charset
import java.util.EnumSet

/**
 * Points the hardcoded `https://cdn.kkutu.io` references (templates, locale messages, the built
 * frontpage bundle) at another host, e.g. a local static server. Active only when `KKUTU_CDN_URL`
 * is set to something other than the default; otherwise the filter is a no-op.
 *
 * It also runs on FORWARD dispatches: the container closes the response when `forward()` returns,
 * so pages served through `forward:/x.html` must be rewritten before that point.
 */
class CdnUrlRewriteFilter(private val target: String) : Filter {
    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        if (request !is HttpServletRequest || response !is HttpServletResponse ||
            request.method != "GET" || request.requestURI.startsWith("/actuator")
        ) {
            chain.doFilter(request, response)
            return
        }
        if (request.dispatcherType == DispatcherType.FORWARD) {
            (request.getAttribute(OUTER_ATTRIBUTE) as? RewritingResponse)?.passThrough = true
        }
        val wrapper = RewritingResponse(response, target)
        if (request.dispatcherType == DispatcherType.REQUEST) request.setAttribute(OUTER_ATTRIBUTE, wrapper)
        chain.doFilter(request, wrapper)
        wrapper.finish()
    }

    internal class RewritingResponse(response: HttpServletResponse, private val target: String) : HttpServletResponseWrapper(response) {
        @Volatile var passThrough = false
        private var buffer: ByteArrayOutputStream? = null
        private var stream: ServletOutputStream? = null
        private var writer: PrintWriter? = null
        private var decided = false
        private var committed = false

        private fun textual(): Boolean {
            val type = contentType?.substringBefore(';')?.trim()?.lowercase() ?: return false
            return type.startsWith("text/") || type == "application/json" || type.endsWith("+json") ||
                type == "application/javascript" || type == "application/x-javascript"
        }

        private fun buffering(): Boolean {
            if (passThrough) return false
            if (!decided) {
                decided = true
                if (textual()) buffer = ByteArrayOutputStream()
            }
            return buffer != null
        }

        override fun getOutputStream(): ServletOutputStream {
            if (!buffering()) return super.getOutputStream()
            return stream ?: object : ServletOutputStream() {
                override fun isReady() = true
                override fun setWriteListener(listener: WriteListener?) {}
                override fun write(b: Int) = buffer!!.write(b)
                override fun write(b: ByteArray, off: Int, len: Int) = buffer!!.write(b, off, len)
                override fun close() = commit()
            }.also { stream = it }
        }

        override fun getWriter(): PrintWriter {
            if (!buffering()) return super.getWriter()
            return writer ?: PrintWriter(OutputStreamWriter(outputStream, charset())).also { writer = it }
        }

        private fun charset(): Charset = runCatching { Charset.forName(characterEncoding) }.getOrDefault(Charsets.UTF_8)

        // The body length may change, so the handler's Content-Length is dropped; the container then
        // either chunks the response or commit() sets the rewritten length.
        override fun setContentLength(len: Int) { if (passThrough) super.setContentLength(len) }
        override fun setContentLengthLong(len: Long) { if (passThrough) super.setContentLengthLong(len) }
        override fun setHeader(name: String, value: String?) {
            if (passThrough || !name.equals("Content-Length", true)) super.setHeader(name, value)
        }
        override fun addHeader(name: String, value: String?) {
            if (passThrough || !name.equals("Content-Length", true)) super.addHeader(name, value)
        }
        override fun flushBuffer() { if (!buffering()) super.flushBuffer() }

        private fun commit() {
            val buf = buffer ?: return
            if (committed) return
            committed = true
            writer?.flush()
            val rewritten = rewrite(buf.toString(charset()), target).toByteArray(charset())
            super.setContentLength(rewritten.size)
            super.getOutputStream().write(rewritten)
            super.flushBuffer()
        }

        fun finish() = commit()
    }

    companion object {
        const val DEFAULT_CDN_URL = "https://cdn.kkutu.io"
        private const val DEFAULT_CDN_HOST_RELATIVE = "//cdn.kkutu.io"
        private const val OUTER_ATTRIBUTE = "me.kkutuio.kkutuweb.config.CdnUrlRewriteFilter.OUTER"

        fun rewrite(text: String, target: String): String {
            if (!text.contains("cdn.kkutu.io")) return text
            // JSON embedded in pages escapes slashes (https:\/\/cdn.kkutu.io), so that form is rewritten as well.
            return text.replace(DEFAULT_CDN_URL, target)
                .replace(DEFAULT_CDN_URL.replace("/", "\\/"), target.replace("/", "\\/"))
                .replace(DEFAULT_CDN_HOST_RELATIVE, target.substringAfter(':'))
        }
    }
}

@Configuration
class CdnUrlRewriteConfig {
    @Bean
    fun cdnUrlRewriteFilterRegistration(@Value("\${KKUTU_CDN_URL:}") configured: String): FilterRegistrationBean<CdnUrlRewriteFilter> {
        val target = configured.trim().trimEnd('/').takeIf { it.isNotEmpty() && it != CdnUrlRewriteFilter.DEFAULT_CDN_URL }
        target?.let {
            val uri = URI.create(it)
            require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrEmpty()) { "KKUTU_CDN_URL must be an http(s) URL: $it" }
        }
        return FilterRegistrationBean(CdnUrlRewriteFilter(target ?: "")).apply {
            setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD))
            order = Ordered.HIGHEST_PRECEDENCE
            isEnabled = target != null
        }
    }
}
