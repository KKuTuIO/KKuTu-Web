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

import $ from 'jquery';
import React, {useEffect} from "react";
import {ErrorFeedback, HelpText, HelpTitle, NickInput, OkBtn, SetupTitle} from "./SetupStyle";
import {useForm} from "react-hook-form";
import Axios from "axios";

import './setup.css';

const Setup = () => {
    const {register, handleSubmit, errors} = useForm();

    useEffect(() => {
        $("#Middle").width($(window).width());

        $(window).on('resize', function(e) {
            $("#Middle").width($(window).width());
        })
    }, [])

    const handleSave = (data) => {
        setNick(data.nickname).then(({data: result}) => {
            if (result.success) {
                window.location.href = '/';
            } else {
                alert(Messages[`kkutu.js.error.${result.resultCode}`]);
            }
        })
    }

    const setNick = (nick) => {
        return Axios.post('/api/setup/nick', {
            nick: nick
        });
    }

    return (
        <>
            <SetupTitle>끄투리오에서 사용하실 별명을 입력해 주세요.</SetupTitle>

            <HelpTitle>별명 규칙</HelpTitle>
            <HelpText>1. 별명은 2 ~ 16글자의 영문자, 한글, 숫자, 공백, 특수문자 -, _로 설정하실 수 있습니다.</HelpText>
            <HelpText>2. 별명은 7일마다 변경하실 수 있으며, 500핑을 사용하여 고정할 수 있습니다.</HelpText>
            <HelpText>3. 욕설, 비속어 등이 포함된 별명을 사용할 경우 운영정책에 따라 제재될 수 있습니다.</HelpText>

            <NickInput placeholder={Messages['kkutu.dialog.dress.nickname.placeHolder']} name="nickname"
                       hasError={errors.nickname} autoComplete="off" ref={register({
                required: true,
                minLength: 2,
                maxLength: 15,
                pattern: /^[가-힣a-zA-Z0-9][가-힣a-zA-Z0-9 _-]*[가-힣a-zA-Z0-9]$/i
            })}/>
            {errors.nickname && errors.nickname.type === 'required' && (
                <ErrorFeedback>별명을 입력해 주세요.</ErrorFeedback>
            )}
            {errors.nickname && (errors.nickname.type === 'minLength' || errors.nickname.type === 'maxLength') && (
                <ErrorFeedback>{Messages['kkutu.js.error.600']}</ErrorFeedback>
            )}
            {errors.nickname && errors.nickname.type === 'pattern' && (
                <ErrorFeedback>{Messages['kkutu.js.error.601']}</ErrorFeedback>
            )}

            <OkBtn onClick={handleSubmit(handleSave)}>확인</OkBtn>
        </>
    );
}

export default Setup;
