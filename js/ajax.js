/**************************************************
 * MKOnlinePlayer v2.4
 * Ajax 后台数据交互请求模块
 * 编写：mengkun(https://mkblog.cn)
 * 时间：2018-3-11
 *************************************************/

// ajax加载搜索结果
function ajaxSearch() {
    if(rem.wd === ""){
        layer.msg('搜索内容不能为空', {anim:6});
        return false;
    }
    
    if(rem.loadPage == 1) { // 弹出搜索提示
        var tmpLoading = layer.msg('搜索中', {icon: 16,shade: [0.75,'#000']});
    }
    
    $.ajax({
        type: mkPlayer.method, 
        url: mkPlayer.api, 
        data: "types=search&count=" + mkPlayer.loadcount + "&source=" + rem.source + "&pages=" + rem.loadPage + "&name=" + rem.wd + "&filter_vip=" + mkPlayer.filterVip,
        dataType: mkPlayer.dataType,
        complete: function(XMLHttpRequest, textStatus) {
            if(tmpLoading) layer.close(tmpLoading);    // 关闭加载中动画
        },  // complete
        success: function(jsonData){
            
            // 调试信息输出
            if(mkPlayer.debug) {
                console.debug("搜索结果数：" + jsonData.length);
            }
            
            if(rem.loadPage == 1)   // 加载第一页，清空列表
            {
                if(jsonData.length === 0)   // 返回结果为零
                {
                    layer.msg('没有找到相关歌曲', {anim:6});
                    return false;
                }
                musicList[0].item = [];
                rem.mainList.html('');   // 清空列表中原有的元素
                addListhead();      // 加载列表头
            } else {
                $("#list-foot").remove();     //已经是加载后面的页码了，删除之前的“加载更多”提示
            }
            
            if(jsonData.length === 0)
            {
                addListbar("nomore");  // 加载完了
                return false;
            }
            
            var tempItem = [], no = musicList[0].item.length;
            
            for (var i = 0; i < jsonData.length; i++) {
                no ++;
                tempItem =  {
                    id: jsonData[i].id,  // 音乐ID
                    name: jsonData[i].name,  // 音乐名字
                    artist: jsonData[i].artist[0], // 艺术家名字
                    album: jsonData[i].album,    // 专辑名字
                    source: jsonData[i].source,     // 音乐来源
                    url_id: jsonData[i].url_id,  // 链接ID
                    pic_id: jsonData[i].pic_id,  // 封面ID
                    lyric_id: jsonData[i].lyric_id,  // 歌词ID
                    pic: null,    // 专辑图片
                    url: null   // mp3链接
                };
                musicList[0].item.push(tempItem);   // 保存到搜索结果临时列表中
                addItem(no, tempItem.name, tempItem.artist, tempItem.album);  // 在前端显示
            }
            
            rem.dislist = 0;    // 当前显示的是搜索列表
            rem.loadPage ++;    // 已加载的列数+1
            
            dataBox("list");    // 在主界面显示出播放列表
            refreshList();  // 刷新列表，添加正在播放样式
            
            if(no < mkPlayer.loadcount) {
                addListbar("nomore");  // 没加载满，说明已经加载完了
            } else {
                addListbar("more");     // 还可以点击加载更多
            }
            
            if(rem.loadPage == 2) listToTop();    // 播放列表滚动到顶部
        },   //success
        error: function(XMLHttpRequest, textStatus, errorThrown) {
            layer.msg('搜索结果获取失败 - ' + XMLHttpRequest.status);
            console.error(XMLHttpRequest + textStatus + errorThrown);
        }   // error
    });//ajax
}

// 完善获取音乐信息
// 音乐所在列表ID、音乐对应ID、回调函数
function ajaxUrl(music, callback)
{
    // 已经有数据，直接回调
    if(music.url !== null && music.url !== "err" && music.url !== "") {
        callback(music);
        return true;
    }
    // id为空，赋值链接错误。直接回调
    if(music.id === null) {
        music.url = "err";
        updateMinfo(music); // 更新音乐信息
        callback(music);
        return true;
    }
    
    $.ajax({ 
        type: mkPlayer.method, 
        url: mkPlayer.api,
        data: "types=url&id=" + music.id + "&source=" + music.source,
        dataType: mkPlayer.dataType,
        success: function(jsonData){
            // 调试信息输出
            if(mkPlayer.debug) {
                console.debug("歌曲链接：" + jsonData.url);
            }
            
            // 解决网易云音乐部分歌曲无法播放问题
            if(music.source == "netease") {
                if(jsonData.url === "") {
                    jsonData.url = "https://music.163.com/song/media/outer/url?id=" + music.id + ".mp3";
                } else {
                    jsonData.url = jsonData.url.replace(/m7c.music./g, "m7.music.");
                    jsonData.url = jsonData.url.replace(/m8c.music./g, "m8.music.");
                }
            }
            
            if(jsonData.url === "") {
                music.url = "err";
            } else {
                music.url = jsonData.url;    // 记录结果
            }
            
            updateMinfo(music); // 更新音乐信息
            
            callback(music);    // 回调函数
            return true;
        },   //success
        error: function(XMLHttpRequest, textStatus, errorThrown) {
            layer.msg('歌曲链接获取失败 - ' + XMLHttpRequest.status);
            console.error(XMLHttpRequest + textStatus + errorThrown);
        }   // error 
    }); //ajax
    
}

// 完善获取音乐封面图
// 包含音乐信息的数组、回调函数
function ajaxPic(music, callback)
{
    // 已经有数据，直接回调
    if(music.pic !== null && music.pic !== "err" && music.pic !== "") {
        callback(music);
        return true;
    }
    // pic_id 为空，赋值链接错误。直接回调
    if(music.pic_id === null) {
        music.pic = "err";
        updateMinfo(music); // 更新音乐信息
        callback(music);
        return true;
    }
    
    $.ajax({ 
        type: mkPlayer.method, 
        url: mkPlayer.api,
        data: "types=pic&id=" + music.pic_id + "&source=" + music.source,
        dataType: mkPlayer.dataType,
        success: function(jsonData){
            // 调试信息输出
            if(mkPlayer.debug) {
                console.log("歌曲封面：" + jsonData.url);
            }
            
            if(jsonData.url !== "") {
                music.pic = jsonData.url;    // 记录结果
            } else {
                music.pic = "err";
            }
            
            updateMinfo(music); // 更新音乐信息
            
            callback(music);    // 回调函数
            return true;
        },   //success
        error: function(XMLHttpRequest, textStatus, errorThrown) {
            layer.msg('歌曲封面获取失败 - ' + XMLHttpRequest.status);
            console.error(XMLHttpRequest + textStatus + errorThrown);
        }   // error 
    }); //ajax
    
}

// ajax加载用户歌单
// 参数：歌单网易云 id, 歌单存储 id，回调函数
function ajaxPlayList(lid, id, callback) {
    if(!lid) return false;
    
    // 已经在加载了，跳过
    if(musicList[id].isloading === true) {
        return true;
    }
    
    musicList[id].isloading = true; // 更新状态：列表加载中
    
    $.ajax({
        type: mkPlayer.method, 
        url: mkPlayer.api, 
        data: "types=playlist&id=" + lid,
        dataType: mkPlayer.dataType,
        complete: function(XMLHttpRequest, textStatus) {
            musicList[id].isloading = false;    // 列表已经加载完了
        },  // complete
        success: function(jsonData){
            // 检查数据有效性
            if (!jsonData || !jsonData.playlist) {
                if (mkPlayer.debug) console.error("歌单数据缺失或格式错误", jsonData);
                layer.msg('无法获取歌单信息');
                return;
            }

            // 存储歌单信息
            var tempList = {
                id: lid,    // 列表的网易云 id
                name: jsonData.playlist.name,   // 列表名字
                cover: jsonData.playlist.coverImgUrl,   // 列表封面
                creatorName: jsonData.playlist.creator.nickname,   // 列表创建者名字
                creatorAvatar: jsonData.playlist.creator.avatarUrl,   // 列表创建者头像
                item: []
            };
            
            if(jsonData.playlist.coverImgUrl !== '') {
                tempList.cover = jsonData.playlist.coverImgUrl + "?param=200y200";
            } else {
                tempList.cover = musicList[id].cover;
            }
            
            if(typeof jsonData.playlist.tracks !== undefined || jsonData.playlist.tracks.length !== 0) {
                // 存储歌单中的音乐信息
                for (var i = 0; i < jsonData.playlist.tracks.length; i++) {
                    tempList.item[i] =  {
                        id: jsonData.playlist.tracks[i].id,  // 音乐ID
                        name: jsonData.playlist.tracks[i].name,  // 音乐名字
                        artist: jsonData.playlist.tracks[i].ar[0].name, // 艺术家名字
                        album: jsonData.playlist.tracks[i].al.name,    // 专辑名字
                        source: "netease",     // 音乐来源
                        url_id: jsonData.playlist.tracks[i].id,  // 链接ID
                        pic_id: null,  // 封面ID
                        lyric_id: jsonData.playlist.tracks[i].id,  // 歌词ID
                        pic: jsonData.playlist.tracks[i].al.picUrl + "?param=300y300",    // 专辑图片
                        url: null   // mp3链接
                    };
                }
            }
            
            // 歌单用户 id 不能丢
            if(musicList[id].creatorID) {
                tempList.creatorID = musicList[id].creatorID;
                if(musicList[id].creatorID === rem.uid) {   // 是当前登录用户的歌单，要保存到缓存中
                    var tmpUlist = playerReaddata('ulist');    // 读取本地记录的用户歌单
                    if(tmpUlist) {  // 读取到了
                        for(i=0; i<tmpUlist.length; i++) {  // 匹配歌单
                            if(tmpUlist[i].id == lid) {
                                tmpUlist[i] = tempList; // 保存歌单中的歌曲
                                playerSavedata('ulist', tmpUlist);  // 保存
                                break;
                            }
                        }
                    }
                }
            }
            
            // 存储列表信息
            musicList[id] = tempList;
            
            // 首页显示默认列表
            if(id == mkPlayer.defaultlist) loadList(id);
            if(callback) callback(id);    // 调用回调函数
            
            // 改变前端列表
            $(".sheet-item[data-no='" + id + "'] .sheet-cover").attr('src', tempList.cover);    // 专辑封面
            $(".sheet-item[data-no='" + id + "'] .sheet-name").html(tempList.name);     // 专辑名字
            
            // 调试信息输出
            if(mkPlayer.debug) {
                console.debug("歌单 [" +tempList.name+ "] 中的音乐获取成功");
            }
        },   //success
        error: function(XMLHttpRequest, textStatus, errorThrown) {
            layer.msg('歌单读取失败 - ' + XMLHttpRequest.status);
            console.error(XMLHttpRequest + textStatus + errorThrown);
            $(".sheet-item[data-no='" + id + "'] .sheet-name").html('<span style="color: #EA8383">读取失败</span>');     // 专辑名字
        }   // error  
    });//ajax
}

// ajax加载歌词
// 参数：音乐对象，回调函数
// 增加一次自动重试机制：请求失败后延时重试 1 次
function ajaxLyric(music, callback, retry, force) {
    lyricTip(force ? '歌词重新加载中...' : '歌词加载中...');

    // 如果没有歌词ID，则提示无歌词并清理状态
    if(!music.lyric_id) {
        rem.lyric = '';
        lyricTip('没有歌词');
        return;
    }

    // 若存在上一次未完成的歌词请求，先中止，避免长时间挂起导致 UI 一直停留在“加载中”
    try {
        if (rem.lyricAjax && rem.lyricAjax.readyState !== 4) {
            rem.lyricAjax.abort();
        }
    } catch (e) {}

    var dataStr = "types=lyric&id=" + music.lyric_id + "&source=" + music.source;
    if (force) {
        dataStr += "&_t=" + Date.now();
    }

    rem.lyricAjax = $.ajax({
        type: mkPlayer.method,
        url: mkPlayer.api,
        data: dataStr,
        dataType: mkPlayer.dataType,
        cache: false,
        // 避免接口长时间无响应导致一直卡在“歌词加载中..."
        timeout: 20000,
        success: function(jsonData){
            // 调试信息输出
            if (mkPlayer.debug) {
                console.debug("歌词获取成功");
            }
            
            if (jsonData.lyric) {
                // 传入歌曲ID + 歌词ID：用于回调侧做更稳健的幂等性校验（兼容不同来源的类型差异）
                callback(jsonData.lyric, music.id, music.lyric_id);
            } else {
                // 成功但无歌词：明确提示“没有歌词”，并清理状态
                rem.lyric = '';
                lyricTip('没有歌词');
            }
        },   //success
        error: function(XMLHttpRequest, textStatus, errorThrown) {
            // 主动 abort 的请求不提示失败
            if (textStatus === 'abort') {
                return;
            }
            layer.msg('歌词读取失败 - ' + XMLHttpRequest.status);
            console.error(XMLHttpRequest + textStatus + errorThrown);
            // 失败时自动重试一次（仅重试 1 次）
            if (!retry) {
                setTimeout(function(){
                    ajaxLyric(music, callback, true, force);
                }, 800);
            } else {
                // 二次失败：明确提示请求失败，并清理状态
                rem.lyric = '';
                lyricTip('歌词读取失败');
            }
        }   // error   
    });//ajax
}


// ajax加载用户的播放列表
// 参数 用户的网易云 id
function ajaxUserList(uid)
{
    var tmpLoading = layer.msg('加载中...', {icon: 16,shade: [0.75,'#000']});
    $.ajax({
        type: mkPlayer.method,
        url: mkPlayer.api,
        data: "types=userlist&uid=" + uid,
        dataType: mkPlayer.dataType,
        complete: function(XMLHttpRequest, textStatus) {
            if(tmpLoading) layer.close(tmpLoading);    // 关闭加载中动画
        },  // complete
        success: function(jsonData){
            if(jsonData.code == "-1" || jsonData.code == 400){
                layer.msg('用户 uid 输入有误', {anim:6});
                return false;
            }
            
            if(jsonData.playlist.length === 0 || typeof(jsonData.playlist.length) === "undefined")
            {
                layer.msg('没找到用户 ' + uid + ' 的歌单', {anim:6});
                return false;
            }else{
                var tempList,userList = [];
                $("#sheet-bar").remove();   // 移除登陆条
                rem.uid = uid;  // 记录已同步用户 uid
                rem.uname = jsonData.playlist[0].creator.nickname;  // 第一个列表(喜欢列表)的创建者即用户昵称
                layer.msg('欢迎您 '+rem.uname);
                // 记录登录用户
                playerSavedata('uid', rem.uid);
                playerSavedata('uname', rem.uname);
                
                for (var i = 0; i < jsonData.playlist.length; i++)
                {
                    // 获取歌单信息
                    tempList = {
                        id: jsonData.playlist[i].id,    // 列表的网易云 id
                        name: jsonData.playlist[i].name,   // 列表名字
                        cover: jsonData.playlist[i].coverImgUrl  + "?param=200y200",   // 列表封面
                        creatorID: uid,   // 列表创建者id
                        creatorName: jsonData.playlist[i].creator.nickname,   // 列表创建者名字
                        creatorAvatar: jsonData.playlist[i].creator.avatarUrl,   // 列表创建者头像
                        item: []
                    };
                    // 存储并显示播放列表
                    addSheet(musicList.push(tempList) - 1, tempList.name, tempList.cover);
                    userList.push(tempList);
                }
                playerSavedata('ulist', userList);
                // 显示退出登录的提示条
                sheetBar();
            }
            // 调试信息输出
            if(mkPlayer.debug) {
                console.debug("用户歌单获取成功 [用户网易云ID：" + uid + "]");
            }
        },   //success
        error: function(XMLHttpRequest, textStatus, errorThrown) {
            layer.msg('歌单同步失败 - ' + XMLHttpRequest.status);
            console.error(XMLHttpRequest + textStatus + errorThrown);
        }   // error
    });//ajax
    return true;
}

// 智能推荐相关功能
// 加载推荐歌单
function loadRecommendList(listIndex) {
    // 优先使用用户配置的域名，否则回落为当前页面的域名（自动带 http/https）
    var domain = mkPlayer.recommendDomain && mkPlayer.recommendDomain.trim()
        ? mkPlayer.recommendDomain.trim()
        : (location.protocol + '//' + location.host);
    if (domain.slice(-1) === '/') domain = domain.slice(0, -1);

    // Token 是必填项
    var token = mkPlayer.recommendToken ? mkPlayer.recommendToken.trim() : "";
    if (!token) {
        layer.msg('请先在设置中配置推荐服务 Token', {icon: 5, time: 3000});
        return;
    }

    var loadingMsg = layer.msg('正在分析您的喜好...', {icon: 16, shade: [0.25, '#000'], time: 0});

    // 定义核心推荐请求函数
    var requestRecommendation = function(favList) {
        if (!favList || favList.length === 0) {
            favList = ["流行音乐"]; // 默认兜底
        }

        $.ajax({
            type: "POST",
            url: domain + "/api/v1/recommend/music?async=true",
            headers: {
                "Authorization": "Bearer " + token,
                "Content-Type": "application/json"
            },
            data: JSON.stringify({ favorites: favList }),
            dataType: "json",
            success: function(data) {
                if (data.task_id) {
                    layer.msg('正在为您生成个性化推荐...', {icon: 16, shade: [0.25, '#000'], time: 0});
                    pollRecommendTask(data.task_id, listIndex, loadingMsg, token);
                } else {
                    layer.close(loadingMsg);
                    layer.msg('推荐服务未返回任务ID', {icon: 5});
                }
            },
            error: function(xhr) {
                layer.close(loadingMsg);
                if (xhr.status === 401) {
                    layer.msg('鉴权失败(401)：请检查设置中的Token是否正确', {icon: 5, time: 3000});
                } else {
                    var errMsg = '推荐请求失败';
                    if (xhr.responseJSON && xhr.responseJSON.error) {
                        errMsg += ': ' + xhr.responseJSON.error;
                    } else {
                        errMsg += ': ' + xhr.status;
                    }
                    layer.msg(errMsg, {icon: 5});
                }
            }
        });
    };

    // 先尝试获取收藏列表
    $.ajax({
        type: mkPlayer.method,
        url: mkPlayer.api,
        data: "types=collections&action=list",
        dataType: mkPlayer.dataType,
        success: function(jsonData) {
            var favorites = [];
            if (jsonData.success && jsonData.collections) {
                // 根据设置决定“参考收藏数量”（默认沿用历史行为：20）
                var targetCount = parseInt(mkPlayer.recommendFavCount, 10);
                if (isNaN(targetCount) || targetCount <= 0) targetCount = 20;

                // 使用副本避免污染原始数据
                var collectionList = (jsonData.collections || []).slice();
                var total = collectionList.length;

                // Fisher-Yates Shuffle（全量打乱）
                for (var s = total - 1; s > 0; s--) {
                    var r = Math.floor(Math.random() * (s + 1));
                    var t = collectionList[s];
                    collectionList[s] = collectionList[r];
                    collectionList[r] = t;
                }

                if (total > 0) {
                    if (targetCount < total) {
                        // 小于收藏总数：随机抽取 targetCount 条
                        for (var i = 0; i < targetCount; i++) {
                            if (collectionList[i] && collectionList[i].name) {
                                favorites.push(collectionList[i].name);
                            }
                        }
                    } else {
                        // 大于等于收藏总数：收藏打乱后全部传递
                        for (var k = 0; k < total; k++) {
                            if (collectionList[k] && collectionList[k].name) {
                                favorites.push(collectionList[k].name);
                            }
                        }
                    }
                }
            }
            
            // 如果收藏为空，尝试使用播放历史
            if (favorites.length === 0) {
                 if (musicList[2].item && musicList[2].item.length > 0) {
                    for (var i = 0; i < Math.min(musicList[2].item.length, 10); i++) {
                         if(musicList[2].item[i].name) favorites.push(musicList[2].item[i].name);
                    }
                }
            }
            
            requestRecommendation(favorites);
        },
        error: function() {
            // 获取收藏失败，尝试使用播放历史
            var favorites = [];
             if (musicList[2].item && musicList[2].item.length > 0) {
                for (var i = 0; i < Math.min(musicList[2].item.length, 10); i++) {
                     if(musicList[2].item[i].name) favorites.push(musicList[2].item[i].name);
                }
            }
            requestRecommendation(favorites);
        }
    });
}

// 轮询推荐任务状态
function pollRecommendTask(taskId, listIndex, loadingMsg, token) {
    // 与请求保持同一域名逻辑（再次从 mkPlayer 或 location 计算，避免为空）
    var domain = mkPlayer.recommendDomain && mkPlayer.recommendDomain.trim()
        ? mkPlayer.recommendDomain.trim()
        : (location.protocol + '//' + location.host);
    if (domain.slice(-1) === '/') domain = domain.slice(0, -1);
    setTimeout(function() {
        $.ajax({
            type: "GET",
            url: domain + "/api/v1/recommend/result/" + taskId,
            headers: {
                "Authorization": "Bearer " + token
            },
            success: function(data) {
                console.log("轮询任务状态:", data);
                if (data.status === "processing" || data.status === "pending") {
                    pollRecommendTask(taskId, listIndex, loadingMsg, token);
                } else if (data.status === "completed") {
                    processRecommendItems(data.data.items, listIndex, loadingMsg);
                } else if (data.status === "failed") {
                    layer.close(loadingMsg);
                    console.error("任务处理失败:", data.error);
                    layer.msg('推荐任务失败: ' + (data.error || '未知错误'));
                } else {
                    layer.close(loadingMsg);
                    console.warn("未知任务状态:", data.status);
                    layer.msg('未知任务状态: ' + data.status);
                }
            },
            error: function(xhr, textStatus, errorThrown) {
                layer.close(loadingMsg);
                console.error("轮询请求失败:", textStatus, errorThrown, xhr);
                if (textStatus === 'parsererror') {
                    layer.msg('轮询接口返回数据格式错误', {icon: 5});
                } else {
                    layer.msg('查询任务状态失败: ' + xhr.status);
                }
            }
        });
    }, 10000); // 每10秒轮询一次
}

// 处理推荐结果
function processRecommendItems(items, listIndex, loadingMsg) {
    if (!items || items.length === 0) {
        layer.close(loadingMsg);
        layer.msg('没有推荐结果');
        return;
    }

    console.log("推荐服务返回的原始列表:", items);

    musicList[listIndex].item = []; // 清空原有列表
    // 初始化数组，长度与items一致，用于按顺序存放结果
    var results = new Array(items.length);
    var processedCount = 0;
    var totalItems = items.length;

    // 确定搜索源，默认网易云
    var searchSource = rem.source || 'netease';

    function checkDone() {
        processedCount++;
        if (processedCount >= totalItems) {
            // 过滤掉未找到的（undefined或null）
            var filtered = results.filter(function(item) { return item; });

            // 按歌曲名字去重（忽略大小写和前后空格），保留首次出现以保持推荐顺序
            var seen = {};
            var deduped = [];
            for (var i = 0; i < filtered.length; i++) {
                var it = filtered[i];
                var key = (it.name || '').trim().toLowerCase();
                if (!key) {
                    // 无有效名字，直接保留
                    deduped.push(it);
                    continue;
                }
                if (!seen[key]) {
                    seen[key] = true;
                    deduped.push(it);
                }
            }

            musicList[listIndex].item = deduped;
            layer.close(loadingMsg);
            
            // 切换到推荐列表并显示
            rem.dislist = listIndex;
            loadList(listIndex);
            
            layer.msg('智能推荐已更新');
        }
    }

    items.forEach(function(item, index) {
        // 对每一项进行搜索
        // 注意：搜索接口需要一一调用
        searchAndAddSong(item.name, searchSource, function(song) {
            results[index] = song; // 按原顺序存放
            checkDone();
        });
    });
}

// 搜索单曲并返回结果（不操作UI）
function searchAndAddSong(keyword, source, callback) {
    // 增加搜索数量 count=20，防止开启VIP过滤时，前几个结果全部被过滤导致无结果
    $.ajax({
        type: mkPlayer.method, 
        url: mkPlayer.api, 
        data: "types=search&count=20&source=" + source + "&pages=1&name=" + encodeURIComponent(keyword) + "&filter_vip=" + mkPlayer.filterVip,
        dataType: mkPlayer.dataType,
        success: function(jsonData) {
             if (jsonData && jsonData.length > 0) {
                // api.php 后端已根据 filter_vip 参数进行了过滤
                // 即使过滤后，我们也取第一个结果
                var chosenData = jsonData[0];
                
                // 构造歌曲对象
                var song = {
                    id: chosenData.id,
                    name: chosenData.name,
                    artist: chosenData.artist[0],
                    album: chosenData.album,
                    source: chosenData.source,
                    url_id: chosenData.url_id,
                    pic_id: chosenData.pic_id,
                    lyric_id: chosenData.lyric_id,
                    pic: null,
                    url: null
                };

                // 保留源特定的 VIP/付费字段，防止信息丢失
                if (chosenData.fee !== undefined) song.fee = chosenData.fee;
                if (chosenData.pay !== undefined) song.pay = chosenData.pay;
                if (chosenData.privilege !== undefined) song.privilege = chosenData.privilege;

                callback(song);
            } else {
                callback(null); // 未找到
            }
        },
        error: function() {
            callback(null); // 出错
        }
    });
}
