/*
 * KKuTu-Web (https://github.com/horyu1234/KKuTu-Web)
 * Copyright (C) 2021. horyu1234(admin@horyu.me)
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

import React from "react";
import styled from 'styled-components';

const Backdrop = styled.div`
  background-color: #000;
  position: fixed;
  z-index: 500;
  margin: auto;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;
  width: 100%;
  height: 100%;
  opacity: 0.5;
`

const Background = styled.div`
  background-color: #212121;
  position: fixed;
  z-index: 600;
  margin: auto;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;
  width: 350px;
  height: 450px;
  padding: 15px;
`

const Title = styled.h1`
  text-align: center;
  margin-top: 15px;
  color: #FF5252;
  font-size: 32px;
`

const Detail = styled.h3`
  text-align: center;
  font-size: 1rem;
`

const DetailTable = styled.table`
  margin-top: 20px;
  width: 100%;
`

const DetailKey = styled.td`
  padding-right: 10px;
`

const DetailValue = styled.td`
  text-align: right;
`

const Footer = styled.p`
  text-align: center;
  font-size: 10.5px;
  margin-top: 30px;
`

export const LogoutBtn = styled.button`
  margin: 5px auto;
  width: 150px;
  height: 30px;
  cursor: pointer;
`

const Blocked = ({blockInfo}) => {
    const getInquireId = () => {
        const date = new Date();
        return `BLK-${blockInfo.blockType}-${blockInfo.id}-${date.getMonth() + 1}.${date.getDate()}.${date.getHours()}.${date.getMinutes()}`;
    }

    const hideIp = (target) => {
        if (blockInfo.blockType !== 'IP') return target;

        const ip = blockInfo.target;
        const splitIp = ip.split('.')
        return `${splitIp[0]}.${splitIp[1]}.*.*`;
    }

    return (
        <>
            <Backdrop/>
            <Background>
                <a href='/login/logout'>로그아웃</a>
                <Title>계정 이용 제한됨</Title>
                <Detail>운영정책 위반으로 게임 이용이 제한되었습니다.</Detail>
                <DetailTable>
                    <tbody>
                    <tr style={{fontSize: '13px'}}>
                        <DetailKey>고유 번호</DetailKey>
                        <DetailValue>{hideIp(blockInfo.target)}</DetailValue>
                    </tr>
                    <tr style={{fontSize: '13px'}}>
                        <DetailKey>해제 일시</DetailKey>
                        <DetailValue>{blockInfo.pardonTime}</DetailValue>
                    </tr>
                    <tr style={{fontSize: '13px'}}>
                        <DetailKey>정지 기간</DetailKey>
                        <DetailValue>{blockInfo.duration}</DetailValue>
                    </tr>
                    <tr style={{fontSize: '14px', color: '#FF9800'}}>
                        <DetailKey>제한 사유</DetailKey>
                        <DetailValue>{blockInfo.reason}</DetailValue>
                    </tr>
                    <tr style={{fontSize: '14px', color: '#FF9800'}}>
                        <DetailKey>해제까지 남은 시간</DetailKey>
                        <DetailValue>{blockInfo.remain}</DetailValue>
                    </tr>
                    </tbody>
                </DetailTable>

                <Footer>본 조치에 대한 문의가 있으실 경우 아래 고객센터로 문의해주시기 바랍니다.</Footer>
                <LogoutBtn onClick={() => window.location.href = 'https://support.kkutu.io/plugin/support_manager/knowledgebase/view/1'}>고객센터로 이동하기</LogoutBtn>

                <Footer style={{marginTop: '10px', fontSize: '16px'}}>문의 시 사용자 식별을 위하여 <span style={{
                    display: 'inline-block',
                    color: '#A5D6A7',
                    fontWeight: 'bold',
                    userSelect: 'all'
                }}>{getInquireId()}</span> 문구를 포함해주시고,<br/>자세한 문의 내용을 작성해주시기 바랍니다.</Footer>
            </Background>
        </>
    )
}

export default Blocked;