/**************************************************
 * MKOnlinePlayer v2.4
 * 封装函数及UI交互模块
 * 编写：mengkun(https://mkblog.cn)
 * 时间：2018-3-11
 *************************************************/
// 判断是否是移动设备
var isMobile = {
    Android: function() {
        return navigator.userAgent.match(/Android/i) ? true : false;
    },
    BlackBerry: function() {
        return navigator.userAgent.match(/BlackBerry/i) ? true : false;
    },
    iOS: function() {
        return navigator.userAgent.match(/iPhone|iPad|iPod/i) ? true : false;
    },
    Windows: function() {
        return navigator.userAgent.match(/IEMobile/i) ? true : false;
    },
    Screen: function() {
        return document.documentElement.clientWidth < 900 ? true : false;
    },
    any: function() {
        return (isMobile.Android() || isMobile.BlackBerry() || isMobile.iOS() || isMobile.Windows() || isMobile.Screen());
    }
};

// 初始化layui
var layer;
var form;
layui.use(['layer', 'form'], function(){
    layer = layui.layer;
    form = layui.form;
});

$(function(){
    if(mkPlayer.debug) {
        console.warn('播放器调试模式已开启，正常使用时请在 js/player.js 中按说明关闭调试模式');
    }

    rem.isMobile  = isMobile.any();      // 判断是否是移动设备
    rem.webTitle  = document.title;      // 记录页面原本的标题
    rem.errCount  = 0;                   // 连续播放失败的歌曲数归零
    rem.userAgent = navigator.userAgent; // 获取用户userAgent

    window.onresize = function () {
        rem.isMobile = isMobile.any();
        if (navigator.userAgent !== rem.userAgent) {
            location.reload();
        }

        // 修复关闭 F12（开发者工具）后右侧空白：
        // 背景模糊层在窗口尺寸变化后需要重新计算与填充
        // 这里做轻量级防抖重绘，避免频繁触发导致卡顿
        try {
            if (rem.resizeTimer) {
                clearTimeout(rem.resizeTimer);
            }
            rem.resizeTimer = setTimeout(function(){
                // 仅在启用了封面背景时重绘
                if ((mkPlayer.coverbg === true && !rem.isMobile) || (mkPlayer.mcoverbg === true && rem.isMobile)) {
                    var bgUrl = null;
                    if (mkPlayer.bgConfig && mkPlayer.bgConfig.type === 'custom' && mkPlayer.bgConfig.url) {
                        bgUrl = mkPlayer.bgConfig.url;
                    } else if (rem.playlist !== undefined && rem.playid !== undefined && musicList[rem.playlist] && musicList[rem.playlist].item) {
                        var currentMusic = musicList[rem.playlist].item[rem.playid];
                        if (currentMusic && currentMusic.pic) {
                            bgUrl = currentMusic.pic;
                        }
                    }
                    if (!bgUrl) bgUrl = "images/player_cover.png";
                    // 保持与页面一致的高斯模糊效果
                    updateBackground(bgUrl, true);
                }
            }, 200);
        } catch (e) {
            if (mkPlayer.debug) {
                console.warn('背景重绘失败（resize）：', e);
            }
        }
    }

    initProgress();     // 初始化音量条、进度条（进度条初始化要在 Audio 前，别问我为什么……）
    initAudio();    // 初始化 audio 标签，事件绑定

    // 恢复本地存储的播放方式（顺序/随机/单曲），避免刷新后重置为默认
    try {
        var storedOrder = playerReaddata('order');
        // 兼容旧版本可能存成字符串的情况
        if (typeof storedOrder === 'string') {
            var parsed = parseInt(storedOrder, 10);
            storedOrder = isNaN(parsed) ? storedOrder : parsed;
        }
        if (storedOrder === 1 || storedOrder === 2 || storedOrder === 3) {
            rem.order = storedOrder;
        } else {
            // 默认：列表循环（顺序）
            rem.order = 2;
        }
        if (typeof applyOrderUI === 'function') {
            applyOrderUI(rem.order);
        }
    } catch (e) {
        // ignore
    }


    if(rem.isMobile) {  // 加了滚动条插件和没加滚动条插件所操作的对象是不一样的
        rem.sheetList = $("#sheet");
        rem.mainList = $("#main-list");
    } else {
        // 滚动条初始化(只在非移动端启用滚动条控件)
        $("#main-list,#sheet").mCustomScrollbar({
            theme:"minimal",
            advanced:{
                updateOnContentResize: true // 数据更新后自动刷新滚动条
            }
        });

        rem.sheetList = $("#sheet .mCSB_container");
        rem.mainList = $("#main-list .mCSB_container");
    }

    addListhead();  // 列表头
    addListbar("loading");  // 列表加载中

    // 顶部按钮点击处理
    $(".btn").click(function(){
        switch($(this).data("action")) {
            case "player":    // 播放器
                dataBox("player");
            break;
            case "search":  // 搜索
                searchBox();
            break;

            case "playing": // 正在播放
                loadList(1); // 显示正在播放列表
            break;

            case "sheet":   // 播放列表
                // 如果启用了懒加载且尚未加载列表，则先加载
                if(!rem.sheetLoaded) {
                   rem.initSheetList();
                }
                dataBox("sheet");    // 在主界面显示出音乐专辑
            break;

            case "collections":   // 我的收藏
                loadCollections(); // 显示收藏列表
            break;

            case "settings":   // 设置
                settingsBox();
            break;
        }
    });

    // 列表项双击播放
    $(".music-list").on("dblclick",".list-item", function() {
        var num = parseInt($(this).data("no"));
        if(isNaN(num)) return false;

        // Check if we're in collections list (now has a real playlist index)
        if(rem.dislist >= 0 && musicList[rem.dislist] && musicList[rem.dislist].id === 'collections') {
            // User is in a collection playlist, use standard listClick
            listClick(num);
        } else if(rem.dislist == -1) {
            // Fallback for any remaining -1 references (legacy)
            // Get the music data from the stored data
            var music = $(this).data('music');
            if(music) {
                // Set playlist to "正在播放" when playing collection items
                rem.playlist = 1;   // Always set to playing list for collection items

                // Find if the song already exists in playing list
                var tmpid = -1;
                for(var i=0; i<musicList[1].item.length; i++) {
                    if(musicList[1].item[i].id == music.id && musicList[1].item[i].source == music.source) {
                        tmpid = i;
                        break;
                    }
                }

                // If not found, add to playing list
                if(tmpid == -1) {
                    musicList[1].item.push(music);
                    tmpid = musicList[1].item.length - 1;
                }

                playList(tmpid);
            }
        } else {
            listClick(num);
        }
    });

    // 移动端列表项单击播放
    $(".music-list").on("click",".list-item", function() {
        if(rem.isMobile) {
            var num = parseInt($(this).data("no"));
            if(isNaN(num)) return false;

            // Check if we're in collections list (now has a real playlist index)
            if(rem.dislist >= 0 && musicList[rem.dislist] && musicList[rem.dislist].id === 'collections') {
                // User is in a collection playlist, use standard listClick
                listClick(num);
            } else if(rem.dislist == -1) {
                // Fallback for any remaining -1 references (legacy)
                // Get the music data from the stored data
                var music = $(this).data('music');
                if(music) {
                    // Set playlist to "正在播放" when playing collection items
                    rem.playlist = 1;   // Always set to playing list for collection items

                    // Find if the song already exists in playing list
                    var tmpid = -1;
                    for(var i=0; i<musicList[1].item.length; i++) {
                        if(musicList[1].item[i].id == music.id && musicList[1].item[i].source == music.source) {
                            tmpid = i;
                            break;
                        }
                    }

                    // If not found, add to playing list
                    if(tmpid == -1) {
                        musicList[1].item.push(music);
                        tmpid = musicList[1].item.length - 1;
                    }

                    playList(tmpid);
                }
            } else {
                listClick(num);
            }
        }
    });

    // 小屏幕点击右侧小点查看歌曲详细信息
    $(".music-list").on("click",".list-mobile-menu", function() {
        var num = parseInt($(this).parent().data("no"));
        musicInfo(rem.dislist, num);
        return false;
    });

    // 列表鼠标移过显示对应的操作按钮
    $(".music-list").on("mousemove",".list-item", function() {
        var num = parseInt($(this).data("no"));
        if(isNaN(num)) return false;
        // 还没有追加菜单则加上菜单
        if(!$(this).data("loadmenu")) {
            var target = $(this).find(".music-name");
            var isCollectionsList = (rem.dislist >= 0 && musicList[rem.dislist] && musicList[rem.dislist].id === 'collections');
            var html = '<span class="music-name-cult">' +
            target.html() +
            '</span>' +
            '<div class="list-menu" data-no="' + num + '">' +
                '<span class="list-icon icon-play" data-function="play" title="点击播放这首歌"></span>' +
                '<span class="list-icon icon-download list-mobile-menu" title="点击下载这首歌"></span>' +
                '<span class="list-icon icon-share" data-function="share" title="点击分享这首歌"></span>' +
                '<span class="list-icon icon-collect" data-function="collect" title="收藏这首歌"></span>';

            // 如果是收藏列表，添加移动按钮
            if (isCollectionsList) {
                html += '<span class="list-icon icon-move-up" data-function="move-up" title="上移"></span>' +
                        '<span class="list-icon icon-move-down" data-function="move-down" title="下移"></span>';
            }

            // 如果当前显示的是“正在播放”列表，添加“跳过/不喜欢”按钮
            if (rem.dislist === 1) {
                html += '<span class="list-icon icon-skip" data-function="skip" title="不喜欢，跳过并从列表移除"></span>';
            }

            html += '</div>';
            target.html(html);
            $(this).data("loadmenu", true);

            // 检查这首歌的收藏状态
            var music = null;
            if(rem.dislist >= 0 && musicList[rem.dislist] && musicList[rem.dislist].item && musicList[rem.dislist].item[num]) {
                music = musicList[rem.dislist].item[num];
            } else if(rem.dislist == -1) { // Collections list
                music = $(this).data('music');
            }

            if(music) {
                // 检查收藏状态
                $.ajax({
                    type: mkPlayer.method,
                    url: mkPlayer.api,
                    data: "types=collections&action=check&id=" + music.id + "&source=" + music.source,
                    dataType: mkPlayer.dataType,
                    success: function(jsonData) {
                        if (jsonData.success) {
                            var $collectIcon = $('.list-item[data-no="' + num + '"] .icon-collect');
                            if (jsonData.collected) {
                                $collectIcon.addClass('collected');
                                $collectIcon.attr('title', '取消收藏');
                            } else {
                                $collectIcon.removeClass('collected');
                                $collectIcon.attr('title', '收藏这首歌');
                            }
                        }
                    }
                });
            }
        }
    });

    // 列表中的菜单点击
    $(".music-list").on("click",".icon-play,.icon-download,.icon-share,.icon-collect,.icon-move-up,.icon-move-down,.icon-skip", function() {
        var num = parseInt($(this).parent().data("no"));
        if(isNaN(num)) return false;
        switch($(this).data("function")) {
            case "play":    // 播放
                listClick(num);     // 调用列表点击处理函数
            break;
            case "share":   // 分享
                var music = null;
                if(rem.dislist >= 0 && musicList[rem.dislist] && musicList[rem.dislist].item && musicList[rem.dislist].item[num]) {
                    music = musicList[rem.dislist].item[num];
                } else if(rem.dislist == -1) { // Collections list
                    // For collections list, we need to get the music data differently
                    music = $('.list-item[data-no="' + num + '"]').data('music');
                }

                if(music) {
                    // ajax 请求数据
                    ajaxUrl(music, ajaxShare);
                } else {
                    layer.msg('歌曲信息获取失败');
                }
            break;
            case "collect":   // 收藏/取消收藏
                var music = null;
                if(rem.dislist >= 0 && musicList[rem.dislist] && musicList[rem.dislist].item && musicList[rem.dislist].item[num]) {
                    music = musicList[rem.dislist].item[num];
                } else if(rem.dislist == -1) { // Collections list
                    // For collections list, we need to get the music data differently
                    music = $('.list-item[data-no="' + num + '"]').data('music');
                }

                if(music) {
                    var icon = $(this);
                    var isCollected = icon.hasClass('collected');

                    toggleCollection(music);

                    // Update the icon and title immediately
                    if (isCollected) {
                        icon.removeClass('collected');
                        icon.attr('title', '收藏这首歌');
                    } else {
                        icon.addClass('collected');
                        icon.attr('title', '取消收藏');
                    }
                }
            break;
            case "move-up":   // 上移歌曲
                if(rem.dislist >= 0 && musicList[rem.dislist] && musicList[rem.dislist].id === 'collections') {
                    moveCollectionItem(num, -1); // 向上移动
                }
            break;
            case "move-down":   // 下移歌曲
                if(rem.dislist >= 0 && musicList[rem.dislist] && musicList[rem.dislist].id === 'collections') {
                    moveCollectionItem(num, 1); // 向下移动
                }
            break;
            case "skip":   // 跳过并移除正在播放列表中的歌曲
                // 仅在“正在播放”列表中生效
                if (rem.dislist === 1) {
                    removePlayingItem(num, { playNext: true });
                } else {
                    layer.msg('请在“正在播放”列表中使用跳过');
                }
            break;
        }
        return true;
    });

    // 点击加载更多
    $(".music-list").on("click",".list-loadmore", function() {
        $(".list-loadmore").removeClass('list-loadmore');
        $(".list-loadmore").html('加载中...');
        ajaxSearch();
    });

    // 点击专辑显示专辑歌曲
    $("#sheet").on("click",".sheet-cover,.sheet-name", function() {
        var num = parseInt($(this).parent().data("no"));
        
        // 智能推荐歌单处理
        if (musicList[num] && musicList[num].id === 'recommend_playlist') {
             loadRecommendList(num);
             return true;
        }

        // 是用户列表，但是还没有加载数据
        if(musicList[num].item.length === 0 && musicList[num].creatorID) {
            layer.msg('列表读取中...', {icon: 16,shade: [0.25,,'#000'],shadeClose: true,time: 500}); // 0代表加载的风格，支持0-2
            // ajax加载数据
            ajaxPlayList(musicList[num].id, num, loadList);
            return true;
        }
        loadList(num);
    });

    // 点击同步云音乐
    $("#sheet").on("click",".login-in", function() {
        layer.prompt(
        {
            title: '请输入您的网易云 UID',
            // value: '',  // 默认值
            btn: ['确定', '取消', '帮助'],
            shade: [0.25,,'#000'],
            shadeClose: true,
            btn3: function(index, layero){
                layer.open({
                    title: '如何获取您的网易云UID？'
                    ,shade: [0.25,,'#000'] //遮罩透明度
                    ,shadeClose: true
                    ,anim: 0 //0-6的动画形式，-1不开启
                    ,content:
                    '1、首先<a href="http://music.163.com/" target="_blank">点我(http://music.163.com/)</a>打开网易云音乐官网<br>' +
                    '2、然后点击页面右上角的“登录”，登录您的账号<br>' +
                    '3、点击您的头像，进入个人中心<br>' +
                    '4、此时<span style="color:red">浏览器地址栏</span> <span style="color: green">/user/home?id=</span> 后面的<span style="color:red">数字</span>就是您的网易云 UID'
                });
            }
        },
        function(val, index){   // 输入后的回调函数
            if(isNaN(val)) {
                layer.msg('uid 只能是数字',{anim: 6});
                return false;
            }
            layer.close(index);     // 关闭输入框
            ajaxUserList(val);
        });
    });

    // 刷新用户列表
    $("#sheet").on("click",".login-refresh", function() {
        playerSavedata('ulist', '');
        layer.msg('刷新歌单');
        clearUserlist();
    });

    // 退出登录
    $("#sheet").on("click",".login-out", function() {
        playerSavedata('uid', '');
        playerSavedata('ulist', '');
        layer.msg('已退出');
        clearUserlist();
    });

    // 播放、暂停按钮的处理
    $("#music-info").click(function(){
        if(rem.playid === undefined) {
            layer.msg('请先播放歌曲');
            return false;
        }

        // 当前播放歌曲统一以“正在播放”列表（musicList[1]）为准。
        // rem.playlist 可能指向来源列表（如歌单/搜索），当播放队列发生插入/移除时会与实际播放索引不一致。
        musicInfo(1, rem.playid);
    });

    // 播放、暂停按钮的处理
    $(".btn-play").click(function(){
        pause();
    });

    // 循环顺序的处理
    $(".btn-order").click(function(){
        orderChange();
    });
    // 上一首歌
    $(".btn-prev").click(function(){
        prevMusic();
    });

    // 下一首
    $(".btn-next").click(function(){
        nextMusic();
    });

    // 已移除底部不喜欢按钮，统一通过列表菜单中的 icon-skip 操作

    // 静音按钮点击事件
    $(".btn-quiet").click(function(){
        var oldVol;     // 之前的音量值
        if($(this).is('.btn-state-quiet')) {
            oldVol = $(this).data("volume");
            oldVol = oldVol? oldVol: (rem.isMobile? 1: mkPlayer.volume);  // 没找到记录的音量，则重置为默认音量
            $(this).removeClass("btn-state-quiet");     // 取消静音
        } else {
            oldVol = volume_bar.percent;
            $(this).addClass("btn-state-quiet");        // 开启静音
            $(this).data("volume", oldVol); // 记录当前音量值
            oldVol = 0;
        }
        playerSavedata('volume', oldVol); // 存储音量信息
        volume_bar.goto(oldVol);    // 刷新音量显示
        if(rem.audio[0] !== undefined) rem.audio[0].volume = oldVol;  // 应用音量
    });



    if((mkPlayer.coverbg === true && !rem.isMobile) || (mkPlayer.mcoverbg === true && rem.isMobile)) { // 开启了封面背景

        if(rem.isMobile) {  // 移动端采用另一种模糊方案
            $('#blur-img').html('<div class="blured-img" id="mobile-blur"></div><div class="blur-mask mobile-mask"></div>');
        } else {
            // 背景图片初始化
            $('#blur-img').backgroundBlur({
                // imageURL : '', // URL to the image that will be used for blurring
                blurAmount : 50, // 模糊度
                imageClass : 'blured-img', // 背景区应用样式
                overlayClass : 'blur-mask', // 覆盖背景区class，可用于遮罩或额外的效果
                // duration: 0, // 图片淡出时间
                endOpacity : 1 // 图像最终的不透明度
            });
        }

        // 初始化时如果启用了自定义背景，立即应用
        if (mkPlayer.bgConfig && mkPlayer.bgConfig.type === 'custom' && mkPlayer.bgConfig.url) {
             updateBackground(mkPlayer.bgConfig.url);
        }

        $('.blur-mask').fadeIn(1000);   // 遮罩层淡出
    }

    // 图片加载失败处理
    $('img').error(function(){
        $(this).attr('src', 'images/player_cover.png');
    });

    setInterval(function () {
        $('.audio-time').text(getAudioTime());
    }, 1000)
    // 初始化播放列表
    initList();

    // 移动端不显示评论框
    if (rem.isMobile) {
        $('.banner_text').hide();
    } else if (!mkPlayer.comments) {
        $('.banner_text').hide();
    }

    // 页面加载完成后，触发一次 temp 目录的清理
    $.ajax({
        type: mkPlayer.method,
        url: mkPlayer.api,
        data: 'types=cache&target_path=' + encodeURIComponent(mkPlayer.tempPath) + '&file_ext=mp3&minute=2',
        dataType: 'json',
        success: function(jsonData){
            if (mkPlayer.debug) {
                console.log('Temp 目录清理结果:', jsonData);
            }
        },
        error: function(XMLHttpRequest, textStatus, errorThrown) {
            if (mkPlayer.debug) {
                console.error('Temp 目录清理失败:', XMLHttpRequest + textStatus + errorThrown);
            }
        }
    });
});

// 播放时长处理函数
function getAudioTime () {
    var audio = $('audio')[0];
    var duration = audio.duration;
    var currentTime = audio.currentTime;
    if (duration && currentTime) {
        return (formatTime(currentTime) + '/' + formatTime(duration));
    } else {
        return '00:00/00:00';
    }
};

// 展现系统列表中任意首歌的歌曲信息
function musicInfo(list, index) {
    var music;
    if(list == -1) {  // Collections list (legacy)
        var $item = $('.list-item[data-no="' + index + '"]');
        music = $item.data('music');
        if(!music) {
            layer.msg('歌曲信息获取失败');
            return;
        }
    } else if(list >= 0 && musicList[list] && musicList[list].id === 'collections') {  // New collections list approach
        // Collections list with real playlist index
        if(musicList[list] && musicList[list].item && musicList[list].item[index]) {
            music = musicList[list].item[index];
        } else {
            // Fallback to DOM data
            var $item = $('.list-item[data-no="' + index + '"]');
            music = $item.data('music');
        }
        if(!music) {
            layer.msg('歌曲信息获取失败');
            return;
        }
    } else {
        if(!musicList[list] || !musicList[list].item || !musicList[list].item[index]) {
            layer.msg('歌曲信息获取失败');
            return;
        }
        music = musicList[list].item[index];
    }
    var tempStr = '<span class="info-title">歌名：</span>' + music.name +
    '<br><span class="info-title">歌手：</span>' + music.artist +
    '<br><span class="info-title">专辑：</span>' + music.album;

    // 判断该歌曲是否为当前实际播放的歌曲：以“正在播放”队列为准（musicList[1]）
    var curMusic = null;
    try {
        if(rem.playid !== undefined && musicList[1] && musicList[1].item && musicList[1].item[rem.playid]) {
            curMusic = musicList[1].item[rem.playid];
        }
    } catch(e) {}
    var isCurrent = false;
    try {
        if (curMusic && music) {
            var aId = (curMusic.id !== undefined && curMusic.id !== null) ? String(curMusic.id) : '';
            var bId = (music.id !== undefined && music.id !== null) ? String(music.id) : '';
            var aSrc = (curMusic.source !== undefined && curMusic.source !== null) ? String(curMusic.source) : '';
            var bSrc = (music.source !== undefined && music.source !== null) ? String(music.source) : '';
            isCurrent = (aId && bId && aId === bId && aSrc === bSrc);
        }
    } catch(e) {}

    if(isCurrent) {   // 当前正在播放这首歌，那么还可以顺便获取一下时长。。
        try {
            if (rem.audio && rem.audio[0] && isFinite(rem.audio[0].duration)) {
                tempStr += '<br><span class="info-title">时长：</span>' + formatTime(rem.audio[0].duration);
            }
        } catch(e) {}
    }

    tempStr += '<br><span class="info-title">操作：</span>' +
    '<span class="info-btn" onclick="thisDownload(this)" data-list="' + list + '" data-index="' + index + '">下载</span>' +
    '<span style="margin-left: 10px" class="info-btn" onclick="thisDownloadLrc(this)" data-list="' + list + '" data-index="' + index + '">下载歌词</span>' +
    '<span style="margin-left: 10px" class="info-btn" onclick="thisDownloadPic(this)" data-list="' + list + '" data-index="' + index + '">下载封面</span>' +
    '<span style="margin-left: 10px" class="info-btn" onclick="thisShare(this)" data-list="' + list + '" data-index="' + index + '">外链</span>';

    // 仅移动端：从“右下角三个点”里提供收藏入口
    if (rem.isMobile) {
        var infoSongId = (music && music.id !== undefined && music.id !== null) ? String(music.id) : '';
        var infoSource = (music && music.source !== undefined && music.source !== null) ? String(music.source) : '';
        tempStr += '<span style="margin-left: 10px" class="info-btn info-btn-collect" onclick="thisToggleCollect(this)" data-list="' + list + '" data-index="' + index + '" data-songid="' + infoSongId + '" data-source="' + infoSource + '">收藏</span>';
    }

    // 仅在查看“当前正在播放的歌曲”时提供歌词刷新入口，避免对非当前歌曲造成误解
    // 该按钮单独占一行，避免在同一行被挤压换行导致显示难看
    // 注意：重载必须绑定到“正在播放”队列索引，避免来源列表与播放队列索引不一致导致串歌。
    if(isCurrent) {
        var curSongId = (curMusic && curMusic.id !== undefined && curMusic.id !== null) ? String(curMusic.id) : '';
        var curSource = (curMusic && curMusic.source !== undefined && curMusic.source !== null) ? String(curMusic.source) : '';
        tempStr += '<br><span class="info-title">歌词：</span>' +
        '<span class="info-btn" onclick="thisReloadLyric(this)" data-list="1" data-index="' + rem.playid + '" data-songid="' + curSongId + '" data-source="' + curSource + '">重新加载歌词</span>';
    }

    layer.open({
        type: 0,
        shade: [0.25,,'#000'],
        shadeClose: true,
        title: false, //不显示标题
        btn: false,
        content: tempStr,
        success: function(layero, layerIndex) {
            // 仅移动端：打开后检查收藏状态，更新按钮文案
            if (!rem.isMobile) return;
            var $btn = $(layero).find('.info-btn-collect');
            if ($btn.length === 0) return;
            if (!music || music.id === undefined || music.id === null || !music.source) return;

            $.ajax({
                type: mkPlayer.method,
                url: mkPlayer.api,
                data: "types=collections&action=check&id=" + music.id + "&source=" + music.source,
                dataType: mkPlayer.dataType,
                success: function(jsonData) {
                    if (!jsonData || !jsonData.success) return;
                    if (jsonData.collected) {
                        $btn.addClass('collected');
                        $btn.data('collected', true);
                        $btn.text('取消收藏');
                    } else {
                        $btn.removeClass('collected');
                        $btn.data('collected', false);
                        $btn.text('收藏');
                    }
                }
            });
        }
    });

    if(mkPlayer.debug) {
        console.info('id: "' + music.id + '",\n' +
        'name: "' + music.name + '",\n' +
        'artist: "' + music.artist + '",\n' +
        'album: "' + music.album + '",\n' +
        'source: "' + music.source + '",\n' +
        'url_id: "' + music.url_id + '",\n' +
        'pic_id: "' + music.pic_id + '",\n' +
        'lyric_id: "' + music.lyric_id + '",\n' +
        'pic: "' + music.pic + '",\n' +
        'url: ""');
        // 'url: "' + music.url + '"');
    }
}

// 移动端歌曲信息弹窗：收藏/取消收藏
function thisToggleCollect(obj) {
    var list = $(obj).data("list");
    var index = $(obj).data("index");
    var music;

    // 取歌曲信息（与 thisDownload/thisShare 保持一致）
    if(list == -1) {  // Collections list (legacy)
        var $item = $('.list-item[data-no="' + index + '"]');
        music = $item.data('music');
        if(!music) {
            layer.msg('歌曲信息获取失败');
            return;
        }
    } else if(list >= 0 && musicList[list] && musicList[list].id === 'collections') {  // New collections list approach
        if(musicList[list] && musicList[list].item && musicList[list].item[index]) {
            music = musicList[list].item[index];
        } else {
            var $item2 = $('.list-item[data-no="' + index + '"]');
            music = $item2.data('music');
        }
        if(!music) {
            layer.msg('歌曲信息获取失败');
            return;
        }
    } else {
        if(!musicList[list] || !musicList[list].item || !musicList[list].item[index]) {
            layer.msg('歌曲信息获取失败');
            return;
        }
        music = musicList[list].item[index];
    }

    // 先乐观更新按钮文案
    var $btn = $(obj);
    var isCollected = $btn.hasClass('collected') || $btn.data('collected');
    if (isCollected) {
        $btn.removeClass('collected');
        $btn.data('collected', false);
        $btn.text('收藏');
    } else {
        $btn.addClass('collected');
        $btn.data('collected', true);
        $btn.text('取消收藏');
    }

    toggleCollection(music);
}

// 重新加载歌词（用于解决长时间挂起导致一直卡在“歌词加载中...”的问题）
function thisReloadLyric(obj) {
    var list = $(obj).data("list");
    var index = $(obj).data("index");
    var songId = $(obj).data("songid");
    var source = $(obj).data("source");

    // 歌词区只展示当前播放歌曲；非当前歌曲仅提示
    // 当前播放歌曲统一以“正在播放”列表（musicList[1]）为准
    if(rem.playid === undefined || !musicList[1] || !musicList[1].item || !musicList[1].item[rem.playid]) {
        layer.msg('当前播放信息异常');
        return;
    }

    // 若按钮携带了歌曲标识，则做一次强校验，避免由于 UI/索引错位导致误刷新
    try {
        var cur = musicList[1].item[rem.playid];
        var curSongId = (cur && cur.id !== undefined && cur.id !== null) ? String(cur.id) : '';
        var curSource = (cur && cur.source !== undefined && cur.source !== null) ? String(cur.source) : '';
        var btnSongId = (songId !== undefined && songId !== null) ? String(songId) : '';
        var btnSource = (source !== undefined && source !== null) ? String(source) : '';
        if (btnSongId && curSongId && btnSongId !== curSongId) {
            layer.msg('请在当前播放歌曲的信息里操作');
            return;
        }
        if (btnSource && curSource && btnSource !== curSource) {
            layer.msg('请在当前播放歌曲的信息里操作');
            return;
        }
    } catch(e) {}

    // 兼容旧按钮（只有 data-list/data-index）：要求指向播放队列
    if(list !== undefined && index !== undefined) {
        if (parseInt(list, 10) !== 1 || parseInt(index, 10) !== parseInt(rem.playid, 10)) {
            layer.msg('请在当前播放歌曲的信息里操作');
            return;
        }
    }

    var music = musicList[1].item[rem.playid];
    if(!music) {
        layer.msg('歌曲信息获取失败');
        return;
    }

    // 先清理状态，再触发一次强制刷新
    rem.lyric = '';
    rem.lastLyric = -1;
    lyricTip('歌词重新加载中...');

    // 第 4 个参数用于强制刷新（追加 cache-busting，并尝试中止前一个歌词请求）
    // 重新加载完成后，立刻按当前播放进度定位歌词（不必等到下一句歌词时间点）
    ajaxLyric(music, function(str, id, lyricId){
        lyricCallback(str, id, lyricId);
        try {
            if (rem.audio && rem.audio[0]) {
                refreshLyric(rem.audio[0].currentTime);
            }
        } catch (e) {}
    }, false, true);
}

// 展现设置弹窗
function settingsBox() {
    // 记录初始配置，用于取消时恢复
    var initialBgConfig = $.extend({}, mkPlayer.bgConfig); // 深度拷贝
    var isSaved = false; // 标记是否保存

    layer.open({
        type: 1,
        title: '设置',
        shade: [0.25,,'#000'],    // 遮罩颜色深度
        shadeClose: true,
        area: '360px',
        content: $('#layer-settings-box').html(),
        success: function(layero, index){
            // 渲染 checkbox 状态
            if (mkPlayer.autoLoad) {
                $(layero).find("input[name='auto-load']").prop("checked", true);
            } else {
                $(layero).find("input[name='auto-load']").prop("checked", false);
            }

            // 渲染“加载全部列表”开关（默认关闭）
            if (mkPlayer.loadAllSheets) {
                $(layero).find("input[name='load-all-sheets']").prop("checked", true);
            } else {
                $(layero).find("input[name='load-all-sheets']").prop("checked", false);
            }

            if (mkPlayer.hotCommentsOnly) {
                $(layero).find("input[name='hot-comments-only']").prop("checked", true);
            } else {
                $(layero).find("input[name='hot-comments-only']").prop("checked", false);
            }

            if (mkPlayer.filterVip) {
                $(layero).find("input[name='filter-vip']").prop("checked", true);
            } else {
                $(layero).find("input[name='filter-vip']").prop("checked", false);
            }
            if (mkPlayer.skipVip) {
                $(layero).find("input[name='skip-vip']").prop("checked", true);
            } else {
                $(layero).find("input[name='skip-vip']").prop("checked", false);
            }

            // 回填推荐服务配置；如果域名未设置则默认填当前页面的域名（自动带 http/https）
            if (mkPlayer.recommendDomain && mkPlayer.recommendDomain.trim()) {
                $(layero).find("input[name='recommend-domain']").val(mkPlayer.recommendDomain);
            } else {
                var origin = location.protocol + '//' + location.host;
                $(layero).find("input[name='recommend-domain']").val(origin);
            }
            $(layero).find("input[name='recommend-token']").val(mkPlayer.recommendToken);

            // 回填“参考收藏数量”（默认沿用历史行为：20）
            try {
                var favCount = parseInt(mkPlayer.recommendFavCount, 10);
                if (isNaN(favCount) || favCount <= 0) favCount = 20;
                $(layero).find("input[name='recommend-fav-count']").val(favCount);
            } catch (e) {
                $(layero).find("input[name='recommend-fav-count']").val(20);
            }

            // 渲染背景设置状态
            var bgType = (mkPlayer.bgConfig && mkPlayer.bgConfig.type) || 'default';
            $(layero).find("input[name='bg-type'][value='" + bgType + "']").prop("checked", true);

            var bgUrl = (mkPlayer.bgConfig && mkPlayer.bgConfig.url) || '';
            $(layero).find("input[name='bg-url']").val(bgUrl);

            if (bgType === 'custom') {
                $(layero).find('#bg-custom-input-area').show();
            }

            form.render();

            // 绑定 Token 显示/隐藏事件（确保位于弹窗成功回调内，可使用 layero 上下文）
            var $tokenInput = $(layero).find("input[name='recommend-token']");
            var $toggleBtn = $(layero).find("#toggle-token-visibility");
            var $eyeIcon = $(layero).find(".token-eye-icon");

            // 初始为密码态
            $tokenInput.attr('type', 'password');
            $eyeIcon.removeClass('token-eye-open').addClass('token-eye-closed');

            $toggleBtn.off('click').on('click', function(){
                var isPassword = $tokenInput.attr('type') === 'password';
                if (isPassword) {
                    $tokenInput.attr('type', 'text');
                    $eyeIcon.removeClass('token-eye-closed').addClass('token-eye-open');
                } else {
                    $tokenInput.attr('type', 'password');
                    $eyeIcon.removeClass('token-eye-open').addClass('token-eye-closed');
                }
            });

            // 监听背景模式切换
            form.on('radio(bg-type)', function(data){
                if(data.value === 'custom'){
                    $(layero).find('#bg-custom-input-area').slideDown();
                } else {
                    $(layero).find('#bg-custom-input-area').slideUp();
                }
            });

            // 监听图片测试（自定义背景也保持高斯模糊一致性）
            $(layero).find('#test-bg-btn').click(function(){
                var url = $(layero).find("input[name='bg-url']").val();
                if(!url) {
                    layer.msg('请输入图片URL');
                    return;
                }

                var loadIdx = layer.msg('正在测试图片...', {icon: 16, time: 0, shade: 0.01});

                var img = new Image();
                img.onload = function(){
                    layer.close(loadIdx);
                    layer.msg('图片有效，已临时预览（含模糊效果）');
                    // 预览图片，保持与页面一致的模糊效果
                    updateBackground(url, true);
                };
                img.onerror = function(){
                    layer.close(loadIdx);
                    layer.msg('图片加载失败，请检查URL是否正确或允许跨域访问');
                };
                img.src = url;
            });

            // 监听提交
            form.on('submit(settings-submit)', function(data){
                // 获取开关状态
                var isAutoLoad = $(layero).find("input[name='auto-load']").prop("checked");
                var isLoadAllSheets = $(layero).find("input[name='load-all-sheets']").prop("checked");
                var isHotCommentsOnly = $(layero).find("input[name='hot-comments-only']").prop("checked");
                var isFilterVip = $(layero).find("input[name='filter-vip']").prop("checked");
                var isSkipVip = $(layero).find("input[name='skip-vip']").prop("checked");
                
                // 获取推荐服务设置
                var recommendDomain = $(layero).find("input[name='recommend-domain']").val();
                var recommendToken = $(layero).find("input[name='recommend-token']").val();
                var recommendFavCountRaw = $(layero).find("input[name='recommend-fav-count']").val();
                var recommendFavCount = parseInt(recommendFavCountRaw, 10);
                if (isNaN(recommendFavCount) || recommendFavCount <= 0) {
                    recommendFavCount = 20;
                }

                // 获取背景设置
                var newBgType = $(layero).find("input[name='bg-type']:checked").val();
                var newBgUrl = $(layero).find("input[name='bg-url']").val();

                // 如果选择了自定义但没有URL，提示错误
                if (newBgType === 'custom' && !newBgUrl) {
                    layer.msg('请填写自定义图片URL');
                    return false;
                }

                // 更新全局配置
                mkPlayer.autoLoad = isAutoLoad;
                mkPlayer.loadAllSheets = isLoadAllSheets;
                mkPlayer.hotCommentsOnly = isHotCommentsOnly;
                mkPlayer.filterVip = isFilterVip;
                mkPlayer.skipVip = isSkipVip;
                mkPlayer.recommendDomain = recommendDomain;
                mkPlayer.recommendToken = recommendToken;
                mkPlayer.recommendFavCount = recommendFavCount;

                mkPlayer.bgConfig = {
                    type: newBgType,
                    url: newBgUrl
                };

                // 保存到本地存储
                playerSavedata('autoLoad', mkPlayer.autoLoad);
                playerSavedata('loadAllSheets', mkPlayer.loadAllSheets);
                playerSavedata('hotCommentsOnly', mkPlayer.hotCommentsOnly);
                playerSavedata('filterVip', mkPlayer.filterVip);
                playerSavedata('skipVip', mkPlayer.skipVip);
                playerSavedata('recommendDomain', mkPlayer.recommendDomain);
                playerSavedata('recommendToken', mkPlayer.recommendToken);
                playerSavedata('recommendFavCount', mkPlayer.recommendFavCount);
                playerSavedata('bgConfig', mkPlayer.bgConfig);

                // 根据“加载全部列表”开关刷新歌单侧边栏渲染
                try {
                    clearSheet();
                    rem.sheetLoaded = false;
                    rem.initSheetList();
                } catch (e) {
                    if (mkPlayer.debug) {
                        console.warn('刷新歌单列表失败：', e);
                    }
                }

                // 应用背景更改（自定义背景也启用模糊以保持一致性）
                if (newBgType === 'custom') {
                    updateBackground(newBgUrl, true); // 自定义也模糊
                } else {
                    // 如果切回默认，且当前有播放歌曲，则恢复歌曲封面
                    if (rem.playlist !== undefined && rem.playid !== undefined) {
                         var currentMusic = musicList[rem.playlist].item[rem.playid];
                         if (currentMusic && currentMusic.pic) {
                             updateBackground(currentMusic.pic, true); // 默认虚化
                         } else {
                             // 没封面就默认图
                             updateBackground("images/player_cover.png", true);
                         }
                    } else {
                        // 没播放歌曲就默认图
                        updateBackground("images/player_cover.png", true);
                    }
                }

                isSaved = true; // 标记已保存
                layer.msg('设置已保存');
                layer.closeAll('page');
                return false; // 阻止表单跳转
            });
        },
        end: function() {
            // 如果未保存，则恢复之前的背景设置（保持模糊一致性）
            if (!isSaved) {
                if (initialBgConfig.type === 'custom' && initialBgConfig.url) {
                    updateBackground(initialBgConfig.url, true);
                } else {
                    // 恢复默认背景
                    if (rem.playlist !== undefined && rem.playid !== undefined && musicList[rem.playlist] && musicList[rem.playlist].item) {
                         var currentMusic = musicList[rem.playlist].item[rem.playid];
                         if (currentMusic && currentMusic.pic) {
                             updateBackground(currentMusic.pic, true);
                         } else {
                             updateBackground("images/player_cover.png", true);
                         }
                    } else {
                        updateBackground("images/player_cover.png", true);
                    }
                }
            }
        }
    });
}

// 展现搜索弹窗
function searchBox() {
    layer.open({
        type: 1,
        title: false, // 不显示标题
        shade: [0.25,,'#000'],    // 遮罩颜色深度
        shadeClose: true,
        offset: 'auto',
        area: '360px',
        success: function(){
            // 恢复上一次的输入
            $("#search-wd").focus().val(rem.wd);
            $("#music-source input[name='source'][value='" + rem.source + "']").prop("checked", "checked");
            form.render();
        },
        content: $('#layer-form-box').html(),
        cancel: function(){}
    });
}

// 搜索提交
function searchSubmit() {
    var wd = $(".layui-layer #search-wd").val();
    if(!wd) {
        layer.msg('搜索内容不能为空', {anim:6, offset: 't'});
        $("#search-wd").focus();
        return false;
    }
    rem.source = $("#music-source input[name='source']:checked").val();

    layer.closeAll('page');     // 关闭搜索框

    rem.loadPage = 1;   // 已加载页数复位
    rem.wd = wd;    // 搜索词
    ajaxSearch();   // 加载搜索结果
    return false;
}

// 下载正在播放的这首歌
function thisDownload(obj) {
    var list = $(obj).data("list");
    var index = $(obj).data("index");
    var music;
    if(list == -1) {  // Collections list
        var $item = $('.list-item[data-no="' + index + '"]');
        music = $item.data('music');
        if(!music) {
            layer.msg('歌曲信息获取失败');
            return;
        }
    } else if(list >= 0 && musicList[list] && musicList[list].id === 'collections') {  // New collections list approach
        // Collections list with real playlist index
        if(musicList[list] && musicList[list].item && musicList[list].item[index]) {
            music = musicList[list].item[index];
        } else {
            // Fallback to DOM data
            var $item = $('.list-item[data-no="' + index + '"]');
            music = $item.data('music');
        }
        if(!music) {
            layer.msg('歌曲信息获取失败');
            return;
        }
    } else {
        if(!musicList[list] || !musicList[list].item || !musicList[list].item[index]) {
            layer.msg('歌曲信息获取失败');
            return;
        }
        music = musicList[list].item[index];
    }
    ajaxUrl(music, download);
}

// 获取并设置评论
// 下载封面
function thisDownloadPic (obj) {
    var list = $(obj).data("list");
    var index = $(obj).data("index");
    var music;
    if(list == -1) {  // Collections list (legacy)
        var $item = $('.list-item[data-no="' + index + '"]');
        music = $item.data('music');
        if(!music) {
            layer.msg('歌曲信息获取失败');
            return;
        }
    } else if(list >= 0 && musicList[list] && musicList[list].id === 'collections') {  // New collections list approach
        // Collections list with real playlist index
        if(musicList[list] && musicList[list].item && musicList[list].item[index]) {
            music = musicList[list].item[index];
        } else {
            // Fallback to DOM data
            var $item = $('.list-item[data-no="' + index + '"]');
            music = $item.data('music');
        }
        if(!music) {
            layer.msg('歌曲信息获取失败');
            return;
        }
    } else {
        if(!musicList[list] || !musicList[list].item || !musicList[list].item[index]) {
            layer.msg('歌曲信息获取失败');
            return;
        }
        music = musicList[list].item[index];
    }
    layer.closeAll();
    if (music.pic) {
        open(music.pic.split('?')[0].split('@')[0]);
    } else {
        $.ajax({
            type: mkPlayer.method,
            url: mkPlayer.api,
            data: "types=pic&id=" + music.pic_id + "&source=" + music.source,
            dataType: mkPlayer.dataType,
            success: function(jsonData){
                if(mkPlayer.debug) {
                    console.log("歌曲封面：" + jsonData.url);
                }
                if (jsonData.url) {
                    open(jsonData.url.split('?')[0].split('@')[0]);
                } else {
                    layer.msg('没有封面');
                }
            },
            error: function(XMLHttpRequest, textStatus, errorThrown) {
                layer.msg('歌曲封面获取失败 - ' + XMLHttpRequest.status);
                console.error(XMLHttpRequest + textStatus + errorThrown);
            }
        });
    }
}

// 下载歌词
function thisDownloadLrc (obj) {
    var list = $(obj).data("list");
    var index = $(obj).data("index");
    var music;
    if(list == -1) {  // Collections list (legacy)
        var $item = $('.list-item[data-no="' + index + '"]');
        music = $item.data('music');
        if(!music) {
            layer.msg('歌曲信息获取失败');
            return;
        }
    } else if(list >= 0 && musicList[list] && musicList[list].id === 'collections') {  // New collections list approach
        // Collections list with real playlist index
        if(musicList[list] && musicList[list].item && musicList[list].item[index]) {
            music = musicList[list].item[index];
        } else {
            // Fallback to DOM data
            var $item = $('.list-item[data-no="' + index + '"]');
            music = $item.data('music');
        }
        if(!music) {
            layer.msg('歌曲信息获取失败');
            return;
        }
    } else {
        if(!musicList[list] || !musicList[list].item || !musicList[list].item[index]) {
            layer.msg('歌曲信息获取失败');
            return;
        }
        music = musicList[list].item[index];
    }
    layer.closeAll();
    $.ajax({
        type: mkPlayer.method,
        url: mkPlayer.api,
        data: "types=lyric&id=" + music.lyric_id + "&source=" + music.source,
        dataType: mkPlayer.dataType,
        success: function(jsonData){
            // 调试信息输出
            if (mkPlayer.debug) {
                console.debug("歌词获取成功");
            }

            var lyric = jsonData.lyric;
            if (mkPlayer.debug) {
                console.debug("歌词获取成功");
            }
            if (lyric) {
                var artist = music.artist ? ' - ' + music.artist : '';
                var filename = (music.name + artist + '.lrc').replace('/', '&');
                var element = document.createElement('a');
                element.setAttribute('href', 'data:text/plain;charset=utf-8,' + encodeURIComponent(lyric));
                element.setAttribute('download', filename);
                element.style.display = 'none';
                document.body.appendChild(element);
                element.click();
                document.body.removeChild(element);
            } else {
                layer.msg('歌词获取失败');
            }
        },   //success
        error: function(XMLHttpRequest, textStatus, errorThrown) {
            layer.msg('歌词读取失败 - ' + XMLHttpRequest.status);
            console.error(XMLHttpRequest + textStatus + errorThrown);
        }   // error
    });//ajax
}

// 分享正在播放的这首歌
function thisShare(obj) {
    var list = $(obj).data("list");
    var index = $(obj).data("index");
    var music;
    if(list == -1) {  // Collections list (legacy)
        var $item = $('.list-item[data-no="' + index + '"]');
        music = $item.data('music');
        if(!music) {
            layer.msg('歌曲信息获取失败');
            return;
        }
    } else if(list >= 0 && musicList[list] && musicList[list].id === 'collections') {  // New collections list approach
        // Collections list with real playlist index
        if(musicList[list] && musicList[list].item && musicList[list].item[index]) {
            music = musicList[list].item[index];
        } else {
            // Fallback to DOM data
            var $item = $('.list-item[data-no="' + index + '"]');
            music = $item.data('music');
        }
        if(!music) {
            layer.msg('歌曲信息获取失败');
            return;
        }
    } else {
        if(!musicList[list] || !musicList[list].item || !musicList[list].item[index]) {
            layer.msg('歌曲信息获取失败');
            return;
        }
        music = musicList[list].item[index];
    }
    ajaxUrl(music, ajaxShare);
}

// 下载歌曲
// 参数：包含歌曲信息的数组
function download(music) {
    if(music.url == 'err' || music.url == "" || music.url == null) {
        layer.msg('这首歌不支持下载');
        return;
    }
    // 直接打开歌曲的原始 URL 进行下载
    window.open(music.url);
    layer.msg('正在尝试下载歌曲...');
}

// 获取外链的ajax回调函数
// 参数：包含音乐信息的数组
function ajaxShare(music) {
    if(music.url == 'err' || music.url == "" || music.url == null) {
        layer.msg('这首歌不支持外链获取');
        return;
    }

    var tmpHtml = '<p>' + music.artist + ' - ' + music.name + ' 的外链地址为：</p>' +
    '<input class="share-url" onmouseover="this.focus();this.select()" value="' + music.url + '">' +
    '<p class="share-tips">* 获取到的音乐外链有效期较短，请按需使用。</p>';

    layer.open({
        title: '歌曲外链分享'
        ,shade: [0.25,,'#000']
        ,shadeClose: true
        ,content: tmpHtml
    });
}

// 改变背景图像
// 参数：图像地址
// 参数：是否模糊（可选，默认 true）
function updateBackground(url, enableBlur) {
    if (!url) return;

    if (enableBlur === undefined) {
        enableBlur = true;
    }

    // 移动端背景
    if (rem.isMobile) {
        $("#mobile-blur").css('background-image', 'url("' + url + '")');
        // 移动端如果需要去模糊，可能需要修改 css class，暂时保持原样或后续优化
    } else {
        // PC端背景
        if (!enableBlur) {
            // 自定义模式，不模糊 -> 使用 CSS 背景
            $("#blur-img").empty(); // 清除插件可能生成的元素
            $("#blur-img").css({
                'background-image': 'url("' + url + '")',
                'background-position': 'center center',
                'background-repeat': 'no-repeat',
                'background-size': 'cover',
                'opacity': 1
            });

            // 手动添加遮罩层，保持视觉一致性
            // 检查是否存在遮罩层，不存在则添加
            if ($("#blur-img .blur-mask").length === 0) {
                $("#blur-img").append('<div class="blur-mask"></div>');
            }
            $("#blur-img .blur-mask").show();

        } else {
            // 默认模式，模糊 -> 使用插件
            $("#blur-img").css('background-image', ''); // 清除 CSS 背景
            $("#blur-img").backgroundBlur(url);
            $("#blur-img").animate({opacity:"1"}, 2000);

            // 插件会重新生成结构，我们需要确保遮罩层显示
            // 使用 setTimeout 确保插件生成完毕后再操作，虽然 backgroundBlur 是同步的，但保险起见
            // 另外 backgroundBlur 插件的 overlayClass 参数会自动创建带该 class 的 div
            // 只需要确保它是可见的
            $('.blur-mask').fadeIn(1000);
        }
    }
}

// 改变右侧封面图像
// 新的图像地址
function changeCover(music) {
    var img = music.pic;    // 获取歌曲封面
    var animate = false,imgload = false;

    if(!img) {  // 封面为空
        ajaxPic(music, changeCover);    // 获取歌曲封面图
        img == "err";    // 暂时用无图像占个位...
    }

    if(img == "err") {
        img = "images/player_cover.png";
    } else {
        // 只有在默认模式下才更新大背景
        if (!mkPlayer.bgConfig || mkPlayer.bgConfig.type !== 'custom') {
            if(mkPlayer.mcoverbg === true && rem.isMobile)      // 移动端封面
            {
                $("#music-cover").load(function(){
                    $("#mobile-blur").css('background-image', 'url("' + img + '")');
                });
            }
            else if(mkPlayer.coverbg === true && !rem.isMobile)     // PC端封面
            {
                $("#music-cover").load(function(){
                    if(animate) {   // 渐变动画也已完成
                        // 默认模式下启用模糊
                        updateBackground(img, true);
                    } else {
                        imgload = true;     // 告诉下面的函数，图片已准备好
                    }

                });

                // 渐变动画
                $("#blur-img").animate({opacity: "0.2"}, 1000, function(){
                    if(imgload) {   // 如果图片已经加载好了
                        // 默认模式下启用模糊
                        updateBackground(img, true);
                    } else {
                        animate = true;     // 等待图像加载完
                    }
                });
            }
        }
    }

    $("#music-cover").attr("src", img);     // 改变右侧封面
    $(".sheet-item[data-no='1'] .sheet-cover").attr('src', img);    // 改变正在播放列表的图像
}


// 向列表中载入某个播放列表
function loadList(list) {
    if(musicList[list].isloading === true) {
        layer.msg('列表读取中...', {icon: 16,shade: [0.25,,'#000'],time: 500});
        return true;
    }

    rem.dislist = list;     // 记录当前显示的列表

    dataBox("list");    // 在主界面显示出播放列表

    // 调试信息输出
    if(mkPlayer.debug) {
        if(musicList[list].id) {
            console.log('加载播放列表 ' + list + ' - ' + musicList[list].name + '\n' +
            'id: ' + musicList[list].id + ',\n' +
            'name: "' + musicList[list].name + '",\n' +
            'cover: "' + musicList[list].cover + '",\n' +
            'item: []');
        } else {
            console.log('加载播放列表 ' + list + ' - ' + musicList[list].name);
        }
    }

    rem.mainList.html('');   // 清空列表中原有的元素
    addListhead();      // 向列表中加入列表头

    if(!musicList[list] || !musicList[list].item || musicList[list].item.length == 0) {
        addListbar("nodata");   // 列表中没有数据
    } else {

        // 逐项添加数据
        for(var i=0; i<musicList[list].item.length; i++) {
            var tmpMusic = musicList[list].item[i];

            addItem(i + 1, tmpMusic.name, tmpMusic.artist, tmpMusic.album);

            // 音乐链接均有有效期限制,重新显示列表时清空处理
            if(list == 1 || list == 2) tmpMusic.url = "";
        }

        // 列表加载完成后的处理
        if(list == 1 || list == 2) {    // 历史记录和正在播放列表允许清空
            addListbar("clear");    // 清空列表
        }

        if(rem.playlist === undefined) {    // 未曾播放过
            if(mkPlayer.autoplay == true) pause();  // 设置了自动播放，则自动播放
        } else {
            refreshList();  // 刷新列表，添加正在播放样式
        }

        listToTop();    // 播放列表滚动到顶部
        
        // 强制刷新滚动条，防止因容器不可见导致的高度计算错误
        if(!rem.isMobile) {
            $("#main-list").mCustomScrollbar("update");
        }
    }
}

// 加载收藏列表
function loadCollections() {
    var tempCollectionIndex = 999; // Use high index to avoid conflicts with other lists

    dataBox("list");    // 在主界面显示出播放列表

    rem.mainList.html('');   // 清空列表中原有的元素
    addListhead();      // 向列表中加入列表头

    // 显示加载中
    addListbar("loading");   // 加载中提示

    // 从服务器获取收藏列表
    $.ajax({
        type: mkPlayer.method,
        url: mkPlayer.api,
        data: "types=collections&action=list",
        dataType: mkPlayer.dataType,
        success: function(jsonData) {
            if (jsonData.success && jsonData.collections) {
                rem.mainList.html('');   // 清空加载中提示
                addListhead();      // 重新添加列表头

                // Create temporary playlist entry for the collection
                if (!musicList[tempCollectionIndex]) {
                    musicList[tempCollectionIndex] = {};
                }
                musicList[tempCollectionIndex].item = [];
                musicList[tempCollectionIndex].name = '我的收藏';
                musicList[tempCollectionIndex].id = 'collections';

                if(jsonData.collections.length == 0) {
                    addListbar("nodata");   // 列表中没有数据
                } else {
                    // 逐项添加数据
                    for(var i=0; i<jsonData.collections.length; i++) {
                        var tmpMusic = jsonData.collections[i];
                        addItem(i + 1, tmpMusic.name, tmpMusic.artist, tmpMusic.album);

                        // Add to temporary playlist
                        musicList[tempCollectionIndex].item.push(tmpMusic);

                        // 为每个列表项添加数据，以便后续获取完整信息
                        $('.list-item[data-no="' + i + '"]').data('music', tmpMusic);
                    }

                    // Now set this temporary list as the display list
                    rem.dislist = tempCollectionIndex;
                }

                // Add import/export buttons for collection list (both when empty and not empty)
                addListbar("collections_export");    // 添加导出按钮
                addListbar("collections_import");    // 添加导入按钮

                // Add clear button for collection list only when there are items
                if(jsonData.collections.length > 0) {
                    addListbar("clear");    // 清空列表
                }
            } else {
                rem.mainList.html('');   // 清空加载中提示
                addListhead();      // 重新添加列表头
                addListbar("nodata");   // 列表中没有数据
            }
        },
        error: function(XMLHttpRequest, textStatus, errorThrown) {
            rem.mainList.html('');   // 清空加载中提示
            addListhead();      // 重新添加列表头
            addListbar("nodata");   // 列表中没有数据
            console.error('收藏列表加载失败: ' + XMLHttpRequest.status);
        }
    });
}

// 导出收藏列表为JSON文件
function exportCollections() {
    $.ajax({
        type: mkPlayer.method,
        url: mkPlayer.api,
        data: "types=collections&action=list",
        dataType: mkPlayer.dataType,
        success: function(jsonData) {
            if (jsonData.success && jsonData.collections) {
                // Create a JSON file with the collections data
                var dataStr = JSON.stringify(jsonData.collections, null, 2);
                var dataUri = 'data:application/json;charset=utf-8,'+ encodeURIComponent(dataStr);

                var exportFileDefaultName = 'collections.json';

                var linkElement = document.createElement('a');
                linkElement.setAttribute('href', dataUri);
                linkElement.setAttribute('download', exportFileDefaultName);
                linkElement.click();

                layer.msg('收藏列表导出成功');
            } else {
                layer.msg('收藏列表为空或导出失败');
            }
        },
        error: function(XMLHttpRequest, textStatus, errorThrown) {
            layer.msg('收藏列表导出失败 - ' + XMLHttpRequest.status);
            console.error('收藏列表导出失败: ' + XMLHttpRequest + textStatus + errorThrown);
        }
    });
}

// 导入收藏列表JSON文件
function importCollections() {
    // Create a file input element
    var input = document.createElement('input');
    input.type = 'file';
    input.accept = '.json';

    input.onchange = function(event) {
        var file = event.target.files[0];
        if (!file) {
            return;
        }

        var reader = new FileReader();
        reader.onload = function(e) {
            try {
                var importedData = JSON.parse(e.target.result);

                if (!Array.isArray(importedData)) {
                    layer.msg('导入的文件格式不正确');
                    return;
                }

                // Add the imported songs to collections one by one
                var importedCount = 0;
                var totalCount = importedData.length;

                function importNext(index) {
                    if (index >= importedData.length) {
                        layer.msg(`导入完成，成功导入 ${importedCount}/${totalCount} 首歌曲`);

                        // Refresh collections UI after import completes
                        refreshCollectionsUI();

                        return;
                    }

                    var song = importedData[index];

                    // Check if song already exists in collections
                    $.ajax({
                        type: mkPlayer.method,
                        url: mkPlayer.api,
                        data: "types=collections&action=check&id=" + song.id + "&source=" + song.source,
                        dataType: mkPlayer.dataType,
                        success: function(checkData) {
                            if (!checkData.collected) {
                                // Song doesn't exist, add it
                                $.ajax({
                                    type: mkPlayer.method,
                                    url: mkPlayer.api,
                                    data: "types=collections&action=add&id=" + song.id +
                                          "&source=" + song.source +
                                          "&name=" + encodeURIComponent(song.name) +
                                          "&artist=" + encodeURIComponent(song.artist) +
                                          "&album=" + encodeURIComponent(song.album) +
                                          "&pic=" + encodeURIComponent(song.pic || '') +
                                          "&url_id=" + encodeURIComponent(song.url_id) +
                                          "&pic_id=" + encodeURIComponent(song.pic_id) +
                                          "&lyric_id=" + encodeURIComponent(song.lyric_id),
                                    dataType: mkPlayer.dataType,
                                    success: function(addData) {
                                        if (addData.success) {
                                            importedCount++;
                                        }
                                        importNext(index + 1);
                                    },
                                    error: function() {
                                        layer.msg(`导入第 ${index + 1} 首歌曲失败`);
                                        importNext(index + 1);
                                    }
                                });
                            } else {
                                // Song already exists, continue to next
                                importNext(index + 1);
                            }
                        },
                        error: function() {
                            layer.msg(`检查第 ${index + 1} 首歌曲状态失败`);
                            importNext(index + 1);
                        }
                    });
                }

                importNext(0);

            } catch (e) {
                layer.msg('JSON文件格式错误');
                console.error('JSON解析错误: ', e);
            }
        };
        reader.readAsText(file);
    };

    input.click();
}

// 刷新收藏列表界面
function refreshCollectionsUI() {
    // 更新内部的收藏列表数据
    var tempCollectionIndex = 999;

    $.ajax({
        type: mkPlayer.method,
        url: mkPlayer.api,
        data: "types=collections&action=list",
        dataType: mkPlayer.dataType,
        success: function(jsonData) {
            if (jsonData.success && jsonData.collections) {
                if (!musicList[tempCollectionIndex]) {
                    musicList[tempCollectionIndex] = {};
                }
                musicList[tempCollectionIndex].item = jsonData.collections;
                musicList[tempCollectionIndex].name = '我的收藏';
                musicList[tempCollectionIndex].id = 'collections';

                // 如果当前正在查看收藏列表，也需要重新加载界面
                if (rem.dislist >= 0 && musicList[rem.dislist] && musicList[rem.dislist].id === 'collections') {
                    loadCollections();
                }
            }
        },
        error: function() {
            // 如果获取最新数据失败，至少刷新当前界面
            if (rem.dislist >= 0 && musicList[rem.dislist] && musicList[rem.dislist].id === 'collections') {
                loadCollections();
            }
        }
    });
}

// 刷新收藏列表界面 (兼容旧的函数名)
function refreshCollectionUI() {
    refreshCollectionsUI();
}

// 播放列表滚动到顶部
function listToTop() {
    if(rem.isMobile) {
        $("#main-list").animate({scrollTop: 0}, 200);
    } else {
        $("#main-list").mCustomScrollbar("scrollTo", 0, "top");
    }
}

// 滚动到上次播放的歌曲位置（不自动播放）
function scrollToLastPlayed(index) {
    try {
        var idx = (typeof index === 'number') ? index : playerReaddata('playid');
        if (idx === null || idx === undefined) return;
        if (!musicList[1] || !musicList[1].item || idx < 0 || idx >= musicList[1].item.length) return;

        // 等待列表元素渲染完成后再滚动
        setTimeout(function(){
            var $target = $('.list-item[data-no="' + idx + '"]');
            if(!$target.length) return;

            if(rem.isMobile) {
                var $container = $('#main-list');
                var top = $target.position().top + $container.scrollTop() - 80; // 微调让目标不贴边
                $container.animate({scrollTop: top}, 200);
            } else {
                var top = $target.position().top; // 相对 mCSB_container 的位置
                $("#main-list").mCustomScrollbar("scrollTo", top);
            }
        }, 0);
    } catch(e) {
        if (mkPlayer.debug) {
            console.warn('滚动到上次播放位置失败：', e);
        }
    }
}

// 向列表中加入列表头
function addListhead() {
    var html = '<div class="list-item list-head">' +
    '    <span class="music-album">' +
    '        专辑' +
    '    </span>' +
    '    <span class="auth-name">' +
    '        歌手' +
    '    </span>' +
    '    <span class="music-name">' +
    '        歌曲' +
    '    </span>' +
    '</div>';
    rem.mainList.append(html);
}

// 列表中新增一项
// 参数：编号、名字、歌手、专辑
function addItem(no, name, auth, album) {
    var html = '<div class="list-item" data-no="' + (no - 1) + '">' +
    '    <span class="list-num">' + no + '</span>' +
    '    <span class="list-mobile-menu"></span>' +
    '    <span class="music-album">' + album + '</span>' +
    '    <span class="auth-name">' + auth + '</span>' +
    '    <span class="music-name">' + name + '</span>' +
    '</div>';
    rem.mainList.append(html);
}

// 加载列表中的提示条
// 参数：类型（more、nomore、loading、nodata、clear）
function addListbar(types) {
    var html
    switch(types) {
        case "more":    // 还可以加载更多
            html = '<div class="list-item text-center list-loadmore list-clickable" title="点击加载更多数据" id="list-foot">点击加载更多...</div>';
        break;

        case "nomore":  // 数据加载完了
            html = '<div class="list-item text-center" id="list-foot">全都加载完了</div>';
        break;

        case "loading": // 加载中
            html = '<div class="list-item text-center" id="list-foot">播放列表加载中...</div>';
        break;

        case "nodata":  // 列表中没有内容
            html = '<div class="list-item text-center" id="list-foot">可能是个假列表，什么也没有</div>';
        break;

        case "clear":   // 清空列表
            html = '<div class="list-item text-center list-clickable" id="list-foot" onclick="clearDislist();">清空列表</div>';
        break;

        case "collections_export":   // 收藏列表导出
            html = '<div class="list-item text-center list-clickable" id="list-foot" onclick="exportCollections();">导出收藏</div>';
        break;

        case "collections_import":   // 收藏列表导入
            html = '<div class="list-item text-center list-clickable" id="list-foot" onclick="importCollections();">导入收藏</div>';
        break;
    }
    rem.mainList.append(html);
}

// 从“正在播放”列表移除指定位置的歌曲，并根据需要切歌
// 参数：index 要移除的列表索引
// 参数：options { playNext: boolean } 如果移除的是当前播放项，是否自动播放下一首
function removePlayingItem(index, options) {
    options = options || {};

    // 校验基础数据
    if (!musicList[1] || !musicList[1].item || musicList[1].item.length === 0) {
        layer.msg('正在播放列表为空');
        return false;
    }
    if (index < 0 || index >= musicList[1].item.length) {
        return false;
    }

    var removingCurrent = (rem.playid !== undefined && index === rem.playid);

    // 从播放列表中移除
    musicList[1].item.splice(index, 1);

    // 保存更新后的播放列表
    playerSavedata('playing', musicList[1].item);

    // 若当前显示的是“正在播放”列表，则同步更新UI
    if (rem.dislist === 1) {
        // 移除对应的列表项
        $('.list-item[data-no="' + index + '"]').remove();

        // 重新编号后续项的 data-no 与显示序号，同时同步内部菜单索引
        $('.list-item').each(function() {
            var currentDataNo = parseInt($(this).attr('data-no'));
            if (!isNaN(currentDataNo) && currentDataNo > index) {
                var newDataNo = currentDataNo - 1;
                $(this).attr('data-no', newDataNo);
                $(this).find('.list-num').text(newDataNo + 1);
                // 更新操作菜单的索引，确保点击菜单时索引正确
                $(this).find('.list-menu').attr('data-no', newDataNo);
            }
        });
    }

    // 如果列表已经空了，清理状态与UI
    if (musicList[1].item.length === 0) {
        rem.playlist = undefined;
        rem.playid = undefined;

        rem.mainList.html('');
        addListhead();
        addListbar('nodata');
        return true;
    }

    // 维护 rem.playid（先调整索引，再刷新高亮，避免显示与实际不一致）
    if (rem.playid !== undefined) {
        if (rem.playid > index) {
            // 被移除的是当前播放项之前的某一项 -> 当前播放索引左移
            rem.playid = rem.playid - 1;
        } else if (rem.playid === index) {
            // 移除了当前正在播放项 -> 当前索引保持不变，等于下一首
            if (rem.playid >= musicList[1].item.length) {
                rem.playid = 0; // 边界处理，若移除的是最后一首
            }
            if (options.playNext) {
                playList(rem.playid);
            }
        }
    }

    // 刷新列表播放状态样式（在索引更新之后执行）
    refreshList();

    return true;
}

// 将时间格式化为 00:00 的格式
// 参数：原始时间
function formatTime(time){
    var hour,minute,second;
    hour = String(parseInt(time/3600,10));
    if(hour.length == 1) hour='0' + hour;

    minute=String(parseInt((time%3600)/60,10));
    if(minute.length == 1) minute='0'+minute;

    second=String(parseInt(time%60,10));
    if(second.length == 1) second='0'+second;

    if(hour > 0) {
        return hour + ":" + minute + ":" + second;
    } else {
        return minute + ":" + second;
    }
}

// url编码
// 输入参数：待编码的字符串
function urlEncode(String) {
    return encodeURIComponent(String).replace(/'/g,"%27").replace(/"/g,"%22");
}

// 在 ajax 获取了音乐的信息后再进行更新
// 参数：要进行更新的音乐
function updateMinfo(music) {
    // 不含有 id 的歌曲无法更新
    if(!music.id) return false;

    // 循环查找播放列表并更新信息
    for(var i=0; i<musicList.length; i++) {
        if(musicList[i] && musicList[i].item) {  // Check if list and items exist
            for(var j=0; j<musicList[i].item.length; j++) {
                // ID 对上了，那就更新信息
                if(musicList[i].item[j].id == music.id && musicList[i].item[j].source == music.source) {
                    musicList[i].item[j] = music;  // 更新音乐信息 (fixed the comparison operator)
                    j = musicList[i].item.length;   // 一个列表中只找一首，找到了就跳出
                }
            }
        }
    }
}

// 刷新当前显示的列表，如果有正在播放则添加样式
function refreshList() {
    // 还没播放过，不用对比了
    if(rem.playlist === undefined) return true;

    $(".list-playing").removeClass("list-playing");        // 移除其它的正在播放

    if(rem.paused !== true) {   // 没有暂停
        if(rem.dislist != -1 && !(rem.dislist >= 0 && musicList[rem.dislist] && musicList[rem.dislist].id === 'collections')) { // Skip for collections lists
            for(var i=0; i<musicList[rem.dislist].item.length; i++) {
                // 与正在播放的歌曲 id 相同
                if((musicList[rem.dislist].item[i].id !== undefined) &&
                  (musicList[rem.dislist].item[i].id == musicList[1].item[rem.playid].id) &&
                  (musicList[rem.dislist].item[i].source == musicList[1].item[rem.playid].source)) {
                    $(".list-item[data-no='" + i + "']").addClass("list-playing");  // 添加正在播放样式

                    return true;    // 一般列表中只有一首，找到了赶紧跳出
                }
            }
        }
    }

}
// 添加一个歌单
// 参数：编号、歌单名字、歌单封面
function addSheet(no, name, cover) {
    if(!cover) cover = "images/player_cover.png";
    if(!name) name = "读取中...";

    var html = '<div class="sheet-item" data-no="' + no + '">' +
    '    <img class="sheet-cover" src="' +cover+ '">' +
    '    <p class="sheet-name">' +name+ '</p>' +
    '</div>';
    rem.sheetList.append(html);
}
// 清空歌单显示
function clearSheet() {
    rem.sheetList.html('');
}

// 歌单列表底部登陆条
function sheetBar() {
    var barHtml;
    if(playerReaddata('uid')) {
        barHtml = '已同步 ' + rem.uname + ' 的歌单 <span class="login-btn login-refresh">[刷新]</span> <span class="login-btn login-out">[退出]</span>';
    } else {
        barHtml = '我的歌单 <span class="login-btn login-in">[点击同步]</span>';
    }
    barHtml = '<span id="sheet-bar"><div class="clear-fix"></div>' +
    '<div id="user-login" class="sheet-title-bar">' + barHtml +
    '</div></span>';
    rem.sheetList.append(barHtml);
}

// 选择要显示哪个数据区
// 参数：要显示的数据区（list、sheet、player）
function dataBox(choose) {
    $('.btn-box .active').removeClass('active');
    switch(choose) {
        case "list":    // 显示播放列表
            if($(".btn[data-action='player']").css('display') !== 'none') {
                $("#player").hide();
            } else if ($("#player").css('display') == 'none') {
                $("#player").fadeIn();
            }
            $("#main-list").fadeIn();
            $("#sheet").fadeOut();
            if(rem.dislist == 1 || rem.dislist == rem.playlist) {  // 正在播放
                $(".btn[data-action='playing']").addClass('active');
            } else if(rem.dislist == 0) {  // 搜索
                $(".btn[data-action='search']").addClass('active');
            } else if(rem.dislist == -1) {  // 我的收藏
                $(".btn[data-action='collections']").addClass('active');
            } else {  // 其他播放列表
                $(".btn[data-action='sheet']").addClass('active');
            }
        break;

        case "sheet":   // 显示专辑
            if($(".btn[data-action='player']").css('display') !== 'none') {
                $("#player").hide();
            } else if ($("#player").css('display') == 'none') {
                $("#player").fadeIn();
            }
            $("#sheet").fadeIn();
            $("#main-list").fadeOut();
            $(".btn[data-action='sheet']").addClass('active');
        break;

        case "player":  // 显示播放器
            $("#player").fadeIn();
            $("#sheet").fadeOut();
            $("#main-list").fadeOut();
            $(".btn[data-action='player']").addClass('active');
        break;
    }
}

// 将当前歌曲加入播放历史
// 参数：要添加的音乐
function addHis(music) {
    if(rem.playlist == 2) return true;  // 在播放“播放记录”列表则不作改变

    if(musicList[2].item.length > 300) musicList[2].item.length = 299; // 限定播放历史最多是 300 首

    if(music.id !== undefined && music.id !== '') {
        // 检查历史数据中是否有这首歌，如果有则提至前面
        for(var i=0; i<musicList[2].item.length; i++) {
            if(musicList[2].item[i].id == music.id && musicList[2].item[i].source == music.source) {
                musicList[2].item.splice(i, 1); // 先删除相同的
                i = musicList[2].item.length;   // 找到了，跳出循环
            }
        }
    }

    // 再放到第一位
    musicList[2].item.unshift(music);

    playerSavedata('his', musicList[2].item);  // 保存播放历史列表
}

// 初始化播放列表
function initList() {
    // 登陆过，那就读取出用户的歌单，并追加到系统歌单的后面
    if(playerReaddata('uid')) {
        rem.uid = playerReaddata('uid');
        rem.uname = playerReaddata('uname');
        var tmp_ulist = playerReaddata('ulist');    // 读取本地记录的用户歌单

        if(tmp_ulist) musicList.push.apply(musicList, tmp_ulist);   // 追加到系统歌单的后面
    }

    // 恢复本地存储的播放列表和历史记录（不涉及UI渲染）
    for(var i=1; i<musicList.length; i++) {
        if(i == 1) {    // 正在播放列表
            var tmp_item = playerReaddata('playing');
            if(tmp_item) {
                musicList[1].item = tmp_item;
                mkPlayer.defaultlist = 1;
            }
        } else if(i == 2) { // 历史记录列表
            var tmp_item = playerReaddata('his');
            if(tmp_item) {
                musicList[2].item = tmp_item;
            }
        }
    }

    // 首页显示默认列表
    if(mkPlayer.defaultlist >= musicList.length) mkPlayer.defaultlist = 1;  // 超出范围，显示正在播放列表

    // 始终渲染 "正在播放" 和 "历史记录" 列表
    for(var i=1; i<=2 && i<musicList.length; i++) {
        addSheet(i, musicList[i].name, musicList[i].cover);
    }

    if (mkPlayer.autoLoad) {
        rem.initSheetList(); // 渲染其余歌单列表
        if(musicList[mkPlayer.defaultlist].isloading !== true) loadList(mkPlayer.defaultlist);
        // 加载完成后，自动定位到上次播放的歌曲位置
        if (mkPlayer.defaultlist === 1) scrollToLastPlayed();
        rem.sheetLoaded = true;
    } else {
        rem.sheetLoaded = false;
        // 如果不自动加载，侧边栏只有 正在播放 和 历史记录。

        // 但我们仍然需要添加登陆条，因为它位于列表底部
        sheetBar();

        // 确保加载当前默认列表的内容（通常是正在播放）
        mkPlayer.defaultlist = 1; // 强制默认为正在播放列表
        loadList(mkPlayer.defaultlist);
        // 加载完成后，自动定位到上次播放的歌曲位置
        scrollToLastPlayed();
    }
}

// 渲染歌单列表（核心逻辑提取）
rem.initSheetList = function() {
    if(rem.sheetLoaded) return; // 避免重复加载

    // 移除已有的登陆条（如果有）
    $("#sheet-bar").remove();

    // 先优先展示智能推荐歌单图标
    var recommendIndex = -1;
    for (var r=3; r<musicList.length; r++) {
        if (musicList[r] && musicList[r].id === 'recommend_playlist') {
            recommendIndex = r;
            break;
        }
    }
    if (recommendIndex !== -1) {
        // 初始化数据结构，避免空引用
        if (!musicList[recommendIndex].item) {
            musicList[recommendIndex].item = [];
        }
        // 预加载封面以提升首屏可见性
        if (musicList[recommendIndex].cover) {
            try {
                var preloadImg = new Image();
                preloadImg.src = musicList[recommendIndex].cover;
            } catch (e) {}
        }
        // 添加智能推荐歌单到侧边栏（优先）
        addSheet(recommendIndex, musicList[recommendIndex].name, musicList[recommendIndex].cover);
    }

    // 限制显示的非系统/推荐歌单数量（根据设置决定是否全部加载）
    var maxSheets = mkPlayer.loadAllSheets ? Number.MAX_SAFE_INTEGER : 7;
    var addedCount = 0;

    // 显示所有的歌单（从系统歌单开始），跳过已添加的智能推荐以避免重复
    for(var i=3; i<musicList.length; i++) {
        if (i === recommendIndex) continue; // 已优先添加
        if (!musicList[i]) continue;

        // 达到限制数量则停止追加
        if (addedCount >= maxSheets) {
            break;
        }

        // 列表不是用户列表，并且信息为空，需要ajax读取列表
        if(!musicList[i].creatorID && (musicList[i].item == undefined || (i>2 && musicList[i].item.length == 0))) {
            musicList[i].item = [];
            if(musicList[i].id) {   // 列表ID已定义
                // 其他歌单通过 Ajax 异步加载信息
                if (musicList[i].id !== 'recommend_playlist') {
                    // ajax获取列表信息
                    ajaxPlayList(musicList[i].id, i);
                }
            } else {    // 列表 ID 未定义
                if(!musicList[i].name) musicList[i].name = '未命名';
            }
        }

        // 在前端显示出来
        addSheet(i, musicList[i].name, musicList[i].cover);
        addedCount++;
    }

    // 登陆了，但歌单又没有，说明是在刷新歌单
    // 注意：这里可能需要检查是否已经加载过用户歌单
    var tmp_ulist = playerReaddata('ulist');
    if(playerReaddata('uid') && !tmp_ulist) {
        ajaxUserList(rem.uid);
    }

    sheetBar(); // 显示登陆条
    rem.sheetLoaded = true;
};

// 清空用户的同步列表
function clearUserlist() {
    if(!rem.uid) return false;

    // 查找用户歌单起点
    for(var i=1; i<musicList.length; i++) {
        if(musicList[i].creatorID !== undefined && musicList[i].creatorID == rem.uid) break;    // 找到了就退出
    }

    // 删除记忆数组
    musicList.splice(i, musicList.length - i); // 先删除相同的
    musicList.length = i;

    // 刷新列表显示
    clearSheet();
    initList();
}

// 清空当前显示的列表
function clearDislist() {
    if(rem.dislist != -1 && !(rem.dislist >= 0 && musicList[rem.dislist] && musicList[rem.dislist].id === 'collections')) { // Skip for collections lists
        musicList[rem.dislist].item.length = 0;  // 清空内容
        if(rem.dislist == 1) {  // 正在播放列表
            playerSavedata('playing', '');  // 清空本地记录
            $(".sheet-item[data-no='1'] .sheet-cover").attr('src', 'images/player_cover.png');    // 恢复正在播放的封面
        } else if(rem.dislist == 2) {   // 播放记录
            playerSavedata('his', '');  // 清空本地记录
        }
    } else if(rem.dislist >= 0 && musicList[rem.dislist] && musicList[rem.dislist].id === 'collections') {
        // For new collection lists, clear the temporary playlist
        if(musicList[rem.dislist] && musicList[rem.dislist].item) {
            musicList[rem.dislist].item.length = 0;
        }
    } else {
        // For old collections (-1), we can't clear as it doesn't exist in musicList
        layer.msg('收藏列表无法清空');
    }
    layer.msg('列表已被清空');
    dataBox("sheet");    // 在主界面显示出音乐专辑
}

// 刷新播放列表，为正在播放的项添加正在播放中的标识
function refreshSheet() {
    // 调试信息输出
    if(mkPlayer.debug) {
        if(rem.playlist !== undefined && rem.playlist != -1 && musicList[rem.playlist] && musicList[rem.playlist].name) {
            console.log("开始播放列表 " + musicList[rem.playlist].name + " 中的歌曲");
        } else if(rem.playlist == -1) {
            console.log("开始播放收藏列表中的歌曲");
        }
    }

    $(".sheet-playing").removeClass("sheet-playing");        // 移除其它的正在播放

    // Only add sheet-playing class if playlist is valid
    if(rem.playlist != -1) {
        $(".sheet-item[data-no='" + rem.playlist + "']").addClass("sheet-playing"); // 添加样式
    }
}

// 播放器本地存储信息
// 参数：键值、数据
function playerSavedata(key, data) {
    key = 'mkPlayer2_' + key;    // 添加前缀，防止串用
    data = JSON.stringify(data);
    // 存储，IE6~7 不支持HTML5本地存储
    if (window.localStorage) {
        localStorage.setItem(key, data);
    }
}

// 播放器读取本地存储信息
// 参数：键值
// 返回：数据
function playerReaddata(key) {
    if(!window.localStorage) return '';
    key = 'mkPlayer2_' + key;
    return JSON.parse(localStorage.getItem(key));
}

// 显示评论模态框
function showCommentModal(index) {
    if (!rem.comments || rem.comments.length === 0) {
        layer.msg('暂无评论数据');
        return;
    }

    var comment = rem.comments[index];
    if (!comment) {
        layer.msg('暂无评论数据');
        return;
    }
    var user = comment.user || {};
    var avatar = user.avatar || "images/avatar.png";
    var username = user.name || "匿名用户";
    var content = comment.content || "暂无评论内容";
    var time = formatDate(comment.time ? comment.time * 1000 : null);

    // 判断当前评论是热门评论还是普通评论
    var isHotComment = false;
    if (typeof rem.hotCommentsCount !== 'undefined' && index < rem.hotCommentsCount) {
        isHotComment = true;
    }
    var commentType = isHotComment ? "【热门评论】" : "【普通评论】";

    // 更新模态框内容
    $("#comment-avatar").attr("src", avatar);
    $("#comment-username").html('<span style="color: #ff6b35; font-weight: bold;">' + commentType + '</span> ' + username);
    $("#comment-content").text(content);

    // 只有在时间有效时才显示时间
    if (time) {
        $("#comment-time").text(time).show();
    } else {
        $("#comment-time").hide();
    }

    // 更新计数显示
    $("#current-comment").text(index + 1);
    $("#total-comments").text(rem.comments.length);

    // 更新导航按钮状态
    $("#prev-comment").prop("disabled", index === 0);
    $("#next-comment").prop("disabled", index === rem.comments.length - 1);

    // 显示模态框 - 使用新的CSS类避免跳动
    $("#comment-modal").addClass('show');
}

// 格式化日期函数
function formatDate(timestamp) {
    // 检查时间戳是否有效
    if (!timestamp || isNaN(timestamp)) {
        return null; // 返回null表示不显示时间
    }

    var date = new Date(timestamp);

    // 检查日期对象是否有效
    if (isNaN(date.getTime())) {
        return null;
    }

    var year = date.getFullYear();
    var month = ('0' + (date.getMonth() + 1)).slice(-2);
    var day = ('0' + date.getDate()).slice(-2);
    var hours = ('0' + date.getHours()).slice(-2);
    var minutes = ('0' + date.getMinutes()).slice(-2);

    return year + '-' + month + '-' + day + ' ' + hours + ':' + minutes;
}

// 评论模态框事件绑定
$(document).ready(function() {
    // 上一条评论
    $("#prev-comment").click(function() {
        if (!rem.comments) return;
        var currentIndex = parseInt($("#current-comment").text()) - 1;
        if (currentIndex > 0) {
            showCommentModal(currentIndex - 1);
        }
    });

    // 下一条评论
    $("#next-comment").click(function() {
        if (!rem.comments) return;
        var currentIndex = parseInt($("#current-comment").text()) - 1;
        if (currentIndex < rem.comments.length - 1) {
            showCommentModal(currentIndex + 1);
        }
    });

    // 关闭评论模态框
    $("#close-comment").click(function() {
        $("#comment-modal").removeClass('show');
    });

    // 点击模态框外部关闭
    $("#comment-modal").click(function(e) {
        if (e.target === this) {
            $(this).removeClass('show');
        }
    });
});

// 切换收藏状态
function toggleCollection(music) {
    // Check the current status first
    $.ajax({
        type: mkPlayer.method,
        url: mkPlayer.api,
        data: "types=collections&action=check&id=" + music.id + "&source=" + music.source,
        dataType: mkPlayer.dataType,
        success: function(jsonData) {
            var action = jsonData.collected ? 'remove' : 'add';

            $.ajax({
                type: mkPlayer.method,
                url: mkPlayer.api,
                data: "types=collections&action=" + action +
                      "&id=" + music.id +
                      "&source=" + music.source +
                      "&name=" + encodeURIComponent(music.name) +
                      "&artist=" + encodeURIComponent(music.artist) +
                      "&album=" + encodeURIComponent(music.album) +
                      "&pic=" + encodeURIComponent(music.pic || '') +
                      "&url_id=" + encodeURIComponent(music.url_id) +
                      "&pic_id=" + encodeURIComponent(music.pic_id) +
                      "&lyric_id=" + encodeURIComponent(music.lyric_id),
                dataType: mkPlayer.dataType,
                success: function(jsonData) {
                    if (jsonData.success) {
                        if (action === 'add') {
                            layer.msg('已收藏');
                        } else {
                            layer.msg('已取消收藏');
                            // 如果当前正在查看收藏列表，刷新列表以移除该项
                            if (rem.dislist >= 0 && musicList[rem.dislist] && musicList[rem.dislist].id === 'collections') {
                                loadCollections();
                            }
                        }
                    } else {
                        layer.msg(jsonData.message || '操作失败');
                    }
                },
                error: function(XMLHttpRequest, textStatus, errorThrown) {
                    layer.msg('收藏操作失败 - ' + XMLHttpRequest.status);
                    console.error(XMLHttpRequest + textStatus + errorThrown);
                }
            });
        },
        error: function(XMLHttpRequest, textStatus, errorThrown) {
            console.error('收藏状态检查失败: ' + XMLHttpRequest.status);
        }
    });
}

// 重写评论函数，使其点击时显示当前正在显示的评论
function comments(obj) {
    // 每次调用 comments 函数时，生成一个新的评论轮播 ID
    if (typeof rem.currentCommentReqId === 'undefined') {
        rem.currentCommentReqId = 0;
    }
    rem.currentCommentReqId++;
    var thisReqId = rem.currentCommentReqId;

    // 清理之前的评论定时器
    if (rem.commentsTime) {
        clearTimeout(rem.commentsTime);
    }

    $(".banner_text span").text("歌曲热评/评论");
    $(".banner_text a").attr("href", "javascript:;");
    $(".banner_text a").removeAttr("target");
    $(".banner_text img").hide();

    // 添加点击事件来打开评论模态框
    $(".banner_text a").off('click.commentModal').on('click.commentModal', function(e) {
        e.preventDefault();
        if (rem.comments && rem.comments.length > 0) {
            // 使用当前显示的评论索引
            var currentIndex = rem.currentCommentIndex || 0;
            showCommentModal(currentIndex);
        } else {
            layer.msg('暂无评论数据');
        }
    });

    $.ajax({
        type: mkPlayer.method,
        url: mkPlayer.api,
        data: "types=comments&id=" + obj.id + "&source=" + obj.source + "&count=100",
        dataType: mkPlayer.dataType,
        success: function(jsonData){
            // 检查这个响应是否仍然有效（即，用户没有在加载期间切换到其他歌曲）
            if (thisReqId !== rem.currentCommentReqId) {
                return; // 如果请求ID不匹配，说明用户已经切换到其他歌曲，停止处理
            }

            // 保存原始评论数据
            var hotComments = jsonData.hot_comment && jsonData.hot_comment.length ? jsonData.hot_comment : [];
            var normalComments = jsonData.comment && jsonData.comment.length ? jsonData.comment : [];

            // 优先使用热门评论，如果没有热门评论则使用普通评论
            // 如果有热门评论，就将热门评论和前50条普通评论合并
            if (mkPlayer.hotCommentsOnly) {
                if (hotComments.length > 0) {
                    rem.comments = hotComments;
                } else {
                    rem.comments = []; // 如果只看热评但没有热评，则为空
                }
            } else {
                if (hotComments.length > 0) {
                    rem.comments = hotComments.concat(normalComments.slice(0, 50)); // 热门评论 + 前50条普通评论
                } else if (normalComments.length > 0) {
                    rem.comments = normalComments;
                } else {
                    rem.comments = [];
                    return;
                }
            }

            // 保存热门评论数量，用于判断某条评论是否为热门评论
            rem.hotCommentsCount = hotComments.length;

            // 限制评论总数，防止过多（可选，根据需要调整）
            if (rem.comments.length > 150) { // 增加限制到150条（热门+普通评论）
                rem.comments = rem.comments.slice(0, 150);
            }

            // 过滤无效评论项，防止数组中出现 undefined/null 导致后续读取报错
            rem.comments = rem.comments.filter(function(c){
                return c && (typeof c.content === 'string' || (c.content && c.content.toString));
            });

            // 无有效评论直接返回
            if (!rem.comments || rem.comments.length === 0) {
                $(".banner_text span").text("暂无评论数据");
                $(".banner_text img").hide();
                return;
            }

            // 更新顶部链接，但不添加跳转功能（仅用于显示）
            if (obj.source === 'netease') {
                $(".banner_text a").attr("href", "https://music.163.com/#/song?id="+obj.id+"#comment-box");
            } else if (obj.source === 'kugou') {
                $(".banner_text a").attr("href", "https://www.kugou.com/song/#hash="+obj.id);
            } else if (obj.source === 'tencent') {
                $(".banner_text a").attr("href", "https://y.qq.com/n/yqq/song/"+obj.id+".html#comment_box");
            } else if (obj.source === 'xiami') {
                $(".banner_text a").attr("href", "https://www.xiami.com/song/"+obj.id+"#comments");
            } else if (obj.source === 'baidu') {

            }

            // 设置为非外部链接
            $(".banner_text a").removeAttr("target");

            (function nextComment (commentsIndex) {
                // 在每次执行前检查当前请求ID是否仍有效
                if (thisReqId !== rem.currentCommentReqId) {
                    return; // 如果请求ID不匹配，停止评论轮播
                }

                if (commentsIndex === undefined || commentsIndex === rem.comments.length-1) {
                    commentsIndex = 0;
                } else {
                    commentsIndex++;
                }

                // 保存当前评论索引
                rem.currentCommentIndex = commentsIndex;

                // 取当前评论并做好空值保护
                var currentComment = rem.comments && rem.comments[commentsIndex] ? rem.comments[commentsIndex] : null;
                var avatarSrc = (currentComment && currentComment.user && currentComment.user.avatar) ? currentComment.user.avatar : "images/avatar.png";

                // 立即更新文字内容（做好空值保护）
                $(".banner_text span").text(currentComment && currentComment.content ? currentComment.content : "");
                $(".banner_text img").show().attr("src", avatarSrc);

                // 设置定时器切换到下一个评论，不依赖于图片加载
                rem.commentsTime = setTimeout(function () {
                    // 在执行下一次调用前，检查当前请求ID是否仍有效
                    if (thisReqId === rem.currentCommentReqId && rem.comments && rem.comments.length > 0) {
                        nextComment(commentsIndex);
                    }
                }, 5000);  // 设置为5秒，减慢滚动速度
            })()
        },   //success
        error: function(XMLHttpRequest, textStatus, errorThrown) {
            // 即使出错也检查请求ID是否仍然有效
            if (thisReqId !== rem.currentCommentReqId) {
                return; // 如果请求ID不匹配，忽略错误响应
            }
            layer.msg('歌曲评论获取失败 - ' + XMLHttpRequest.status);
            console.error(XMLHttpRequest + textStatus + errorThrown);
        }   // error
    });//ajax
}

// 移动收藏列表中的歌曲位置
// 参数：当前索引，移动方向（-1为上移，1为下移）
function moveCollectionItem(currentIndex, direction) {
    // 确保当前在收藏列表中
    if(rem.dislist < 0 || !musicList[rem.dislist] || musicList[rem.dislist].id !== 'collections') {
        layer.msg('请在收藏列表中操作');
        return;
    }

    var collectionList = musicList[rem.dislist].item;

    // 检查索引范围
    if(currentIndex < 0 || currentIndex >= collectionList.length) {
        layer.msg('无效的歌曲索引');
        return;
    }

    // 计算目标索引
    var targetIndex = currentIndex + direction;

    // 检查目标索引范围
    if(targetIndex < 0 || targetIndex >= collectionList.length) {
        if(direction === -1) {
            layer.msg('已在列表顶部，无法上移');
        } else {
            layer.msg('已在列表底部，无法下移');
        }
        return;
    }

    // 交换位置
    var temp = collectionList[currentIndex];
    collectionList[currentIndex] = collectionList[targetIndex];
    collectionList[targetIndex] = temp;

    // 如果后端支持排序功能，则调用API保存排序
    $.ajax({
        type: mkPlayer.method,
        url: mkPlayer.api,
        data: {
            types: 'collections',
            action: 'reorder',
            collections: JSON.stringify(collectionList)
        },
        dataType: mkPlayer.dataType,
        success: function(response) {
            if(response.success) {
                // 重新加载列表以显示新的顺序
                loadCollections();

                // 显示成功消息
                if(direction === -1) {
                    layer.msg('歌曲已上移');
                } else {
                    layer.msg('歌曲已下移');
                }
            } else {
                // 排序功能可能不存在（比如API未更新），只在前端更新
                // 重新加载列表以显示新的顺序（仅在当前会话中有效）
                loadCollections();

                // 显示成功消息
                if(direction === -1) {
                    layer.msg('歌曲已上移（刷新页面后可能重置）');
                } else {
                    layer.msg('歌曲已下移（刷新页面后可能重置）');
                }
            }
        },
        error: function(XMLHttpRequest, textStatus, errorThrown) {
            // 排序功能不存在或服务器错误，只在前端更新
            // 重新加载列表以显示新的顺序（仅在当前会话中有效）
            loadCollections();

            // 显示提醒消息
            if(direction === -1) {
                layer.msg('歌曲已上移（刷新页面后可能重置）');
            } else {
                layer.msg('歌曲已下移（刷新页面后可能重置）');
            }
            console.info('后端排序功能可能未启用，排序仅在当前会话中有效');
        }
    });
}
