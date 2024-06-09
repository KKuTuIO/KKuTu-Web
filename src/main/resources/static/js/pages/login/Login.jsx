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

import React, {useEffect, useState} from "react";
import Axios from "axios";
import Loading from "../../components/loading/Loading";

import '../../oauth-buttons.min'

import '../../../css/oauth-buttons.min.css';
import '../../../css/in_login.css';
import {BrowserView, MobileView} from "react-device-detect";
import Product from "../../components/Product";
import AdsenseAd from "../../components/AdsenseAd";
import Separator from "../../components/Separator";

const Login = () => {
    const [vendors, setVendors] = useState([]);
    const [loginReason, setLoginReason] = useState('');

    useEffect(() => {
        getVendorList().then(res => setVendors(res.data))
        getLoginReason().then(res => setLoginReason(res.data))
    }, [])

    const getVendorList = () => {
        return Axios.get('/api/login/vendor');
    }

    const getLoginReason = () => {
        return Axios.get('/api/login/reason');
    }

    const handleClickLogin = (vendor) => {
        location.href = `/login/${vendor}`;
    }

    if (vendors.length === 0) return <Loading/>;
    return (
        <>
            {loginReason && <div className="login-reason">{loginReason}</div>}
            <div className="login-with">{Messages['login.title']}</div>

            {
                vendors.map(vendor => (
                    <div key={vendor} className={`lbtn lbtn-${vendor} lbtn-flat white`}
                         onClick={() => handleClickLogin(vendor)}>
                        <i className="logo"/>
                        <p className="label">{Messages[`login.button.${vendor}`]}</p>
                    </div>
                ))
            }

            <div className="login-legal" dangerouslySetInnerHTML={{__html: Messages['login.legal']}}/>

            <BrowserView>
                <Product id="qwShKF" title={Messages['portal.ad.title']} createWithShown={true} width={"calc(100% - 10px)"}>
                    <AdsenseAd client="ca-pub-7356923884519790" slot="5297190912" format="auto" responsive="true"/>
                </Product>
                <Separator height={10}/>
            </BrowserView>
            <MobileView>
                <Product id="qwShKF" title={Messages['portal.ad.title']} createWithShown={true} width={"calc(100% - 10px)"}>
                    <AdsenseAd client="ca-pub-7356923884519790" slot="4321901032" format="auto" responsive="true"/>
                </Product>
                <Separator height={10}/>
            </MobileView>
        </>
    );
}

export default Login;
