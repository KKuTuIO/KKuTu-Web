/** What the 도움말 button of each 말모이 view says. The screens themselves carry no explanations. */
import { t } from '../i18n.svelte.js';
import { LIMITS } from './model.js';

/** Built when asked (not when the module loads), so the words are the ones of the language the page has loaded. */
export const help = () => ({
	list: [
		[t('play.malmoi.help.list.what.head'), t('play.malmoi.help.list.what.body')],
		[t('play.malmoi.help.list.ext.head'), t('play.malmoi.help.list.ext.body')],
		[t('play.malmoi.help.list.scope.head'), t('play.malmoi.help.list.scope.body')],
		[t('play.malmoi.help.reward.head'), t('play.malmoi.help.reward.body')]
	],
	detail: [
		[t('play.malmoi.help.detail.apply.head'), t('play.malmoi.help.detail.apply.body')],
		[t('play.malmoi.help.detail.delete.head'), t('play.malmoi.help.detail.delete.body')],
		[t('play.malmoi.help.detail.managers.head'), t('play.malmoi.help.detail.managers.body', { topDays: LIMITS.topDays, share: Math.round(LIMITS.topShare * 100), blockDays: LIMITS.blockDays })],
		[t('play.malmoi.help.detail.editors.head'), t('play.malmoi.help.detail.editors.body')],
		[t('play.malmoi.help.detail.promote.head'), t('play.malmoi.help.detail.promote.body', { publishLevel: LIMITS.publishLevel, editors: LIMITS.editors, promoteLevel: LIMITS.promoteLevel, promoteWords: LIMITS.promoteWords })],
		[t('play.malmoi.help.license.head'), t('play.malmoi.help.license.body', { fee: LIMITS.fee, days: LIMITS.licenseDays, daily: LIMITS.dailyEdits, delay: 90 })],
		[t('play.malmoi.help.reward.head'), t('play.malmoi.help.reward.body')]
	],
	form: [
		[t('play.malmoi.help.form.criteria.head'), t('play.malmoi.help.form.criteria.body')],
		[t('play.malmoi.help.form.scope.head'), t('play.malmoi.help.form.scope.body', { publishLevel: LIMITS.publishLevel, editors: LIMITS.editors, unlisted: LIMITS.unlisted })],
		[t('play.malmoi.help.form.cover.head'), t('play.malmoi.help.form.cover.body')],
		[t('play.malmoi.help.form.bulk.head'), t('play.malmoi.help.form.bulk.body')],
		[t('play.malmoi.help.license.head'), t('play.malmoi.help.license.body', { fee: LIMITS.fee, days: LIMITS.licenseDays, daily: LIMITS.dailyEdits, delay: 90 })]
	]
});
