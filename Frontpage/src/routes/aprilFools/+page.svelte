<script>
    import './style.css';
</script>
<div class="legacyBG">
    <div id="Middle" style="margin-left: 212.5px;">
        <div style="float: left; width: 100%; margin-top: 5px;"></div>
        <div style="float: left; width: 100%;">
          <div style="float: left; padding-left: 70px;">
            <h3 id="holder"> 글자로 놀자! 끄투 온라인</h3>
            <img id="logo" src="img/kkutu/short_logo.png">
          </div>
          <div class="jjoriping" style="margin-top: 40px;">
            <img class="jjoObj jjoEyeL" src="img/jjoeyeL.png" style="left: -270px;">
            <img class="jjoObj jjoNose" src="img/jjonose.png" style="left: -170px;">
            <img class="jjoObj jjoEyeR" src="img/jjoeyeR.png" style="left: -70px;">
            <div class="jjoDisplayBar" style="width: 325px; height: 85px;">
              <button class="jjo-display" id="game-start">게임 시작!</button>
            </div>
          </div>
        </div>
        <div style="float: right; margin-top: -120px; width: 50%;">
          <div class="server-list-box">
            <h3 class="server-list-title">
              <a id="server-refresh" class="expl-mother">
                <i class="fa fa-refresh"></i>
                <div class="expl" style="width: initial;">
                  <h5>서버 목록을 다시 불러옵니다.</h5>
                </div>
              </a>
              <label>서버 목록</label>
              <label id="server-total" style="color: #AAA; font-size: 13px;">&nbsp;총 317명</label>
            </h3>
            <div id="server-list">
              <div class="server" id="server-0">
                <div class="server-status ss-o"></div>
                <div class="server-name">감자</div>
                <div class="server-people graph">
                  <div class="graph-bar" style="width: 34%;"></div>
                  <label>136 / 400</label>
                </div>
                <div class="server-enter">접속</div>
              </div>
              <div class="server" id="server-1">
                <div class="server-status ss-o"></div>
                <div class="server-name">냉이</div>
                <div class="server-people graph">
                  <div class="graph-bar" style="width: 4.25%;"></div>
                  <label>17 / 400</label>
                </div>
                <div class="server-enter">접속</div>
              </div>
              <div class="server" id="server-2">
                <div class="server-status ss-o"></div>
                <div class="server-name">다래</div>
                <div class="server-people graph">
                  <div class="graph-bar" style="width: 1%;"></div>
                  <label>4 / 400</label>
                </div>
                <div class="server-enter">접속</div>
              </div>
              <div class="server" id="server-3">
                <div class="server-status ss-o"></div>
                <div class="server-name">레몬</div>
                <div class="server-people graph">
                  <div class="graph-bar" style="width: 1%;"></div>
                  <label>4 / 400</label>
                </div>
                <div class="server-enter">접속</div>
              </div>
              <div class="server" id="server-4">
                <div class="server-status ss-o"></div>
                <div class="server-name">망고</div>
                <div class="server-people graph">
                  <div class="graph-bar" style="width: 2.25%;"></div>
                  <label>9 / 400</label>
                </div>
                <div class="server-enter">접속</div>
              </div>
              <div class="server" id="server-5">
                <div class="server-status ss-o"></div>
                <div class="server-name">보리</div>
                <div class="server-people graph">
                  <div class="graph-bar" style="width: 23.75%;"></div>
                  <label>95 / 400</label>
                </div>
                <div class="server-enter">접속</div>
              </div>
              <div class="server" id="server-6">
                <div class="server-status ss-o"></div>
                <div class="server-name">상추</div>
                <div class="server-people graph">
                  <div class="graph-bar" style="width: 2.25%;"></div>
                  <label>9 / 400</label>
                </div>
                <div class="server-enter">접속</div>
              </div>
              <div class="server" id="server-7">
                <div class="server-status ss-o"></div>
                <div class="server-name">아욱</div>
                <div class="server-people graph">
                  <div class="graph-bar" style="width: 3.25%;"></div>
                  <label>13 / 400</label>
                </div>
                <div class="server-enter">접속</div>
              </div>
              <div class="server" id="server-8">
                <div class="server-status ss-o"></div>
                <div class="server-name">자두</div>
                <div class="server-people graph">
                  <div class="graph-bar" style="width: 3.25%;"></div>
                  <label>13 / 400</label>
                </div>
                <div class="server-enter">접속</div>
              </div>
              <div class="server" id="server-9">
                <div class="server-status ss-o"></div>
                <div class="server-name">참외</div>
                <div class="server-people graph">
                  <div class="graph-bar" style="width: 4.25%;"></div>
                  <label>17 / 400</label>
                </div>
                <div class="server-enter">접속</div>
              </div>
            </div>
          </div>
        </div>
        <div style="float: left; width: calc(100% - 4px); border: 2px solid #CCC; margin-top: 10px; background-color: #EEE;">
          <iframe id="kkutu-bulletin" width="100%" height="400px" src="/kkutu_bulletin.html" data-ruffle-polyfilled=""></iframe>
        </div>
        <div style="float: left; width: 100%; margin-top: 10px;"></div>
      </div>
</div>