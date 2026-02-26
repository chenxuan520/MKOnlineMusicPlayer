/**************************************************
 * MKOnlinePlayer v2.31
 * 歌词解析及滚动模块
 * 编写：mengkun(http://mkblog.cn)
 * 时间：2017-9-13
 *************************************************/
 
var lyricArea = $("#lyric");    // 歌词显示容器

// 在歌词区显示提示语（如歌词加载中、无歌词等）
function lyricTip(str) {
    lyricArea.html("<li class='lyric-tip'>"+str+"</li>");     // 显示内容
}

// 歌曲加载完后的回调函数
// 参数：歌词源文件
function lyricCallback(str, id, lyricId, reqToken) {
    // 歌词区始终以“正在播放”队列为准（musicList[1]）。
    // 不能依赖 rem.playlist：在搜索插播/收藏等场景下，rem.playlist 可能不是 1，
    // 导致回包校验取错列表而直接 return，界面就会一直停留在“加载中/重新加载中”。
    if (rem.playid === undefined || !musicList[1] || !musicList[1].item || !musicList[1].item[rem.playid]) {
        return;
    }

    // 若带了请求令牌，则仅允许“最新一次歌词请求”更新 UI
    if (reqToken && rem.lyricReqToken && String(reqToken) !== String(rem.lyricReqToken)) {
        return;
    }

    // 返回的歌词不是当前这首歌的，跳过
    // 兼容不同来源/缓存导致的 id 类型差异（数字 vs 字符串），并允许用 lyric_id 进行校验。
    var cur = musicList[1].item[rem.playid];
    var curSongId = (cur && cur.id !== undefined && cur.id !== null) ? String(cur.id) : '';
    var cbSongId = (id !== undefined && id !== null) ? String(id) : '';
    var curLyricId = (cur && cur.lyric_id !== undefined && cur.lyric_id !== null) ? String(cur.lyric_id) : '';
    var cbLyricId = (lyricId !== undefined && lyricId !== null) ? String(lyricId) : '';

    var matchBySongId = cbSongId && curSongId && cbSongId === curSongId;
    var matchByLyricId = cbLyricId && curLyricId && cbLyricId === curLyricId;
    // 兼容部分源（如 kugou hash）大小写差异
    if (!matchBySongId && cbSongId && curSongId && cbSongId.toLowerCase && curSongId.toLowerCase) {
        matchBySongId = cbSongId.toLowerCase() === curSongId.toLowerCase();
    }
    if (!matchByLyricId && cbLyricId && curLyricId && cbLyricId.toLowerCase && curLyricId.toLowerCase) {
        matchByLyricId = cbLyricId.toLowerCase() === curLyricId.toLowerCase();
    }
    // 当 token 校验通过时，允许跳过 id/lyricId 的严格匹配（避免因为字段类型/大小写等导致 UI 卡住）
    if (!reqToken && !matchBySongId && !matchByLyricId) return;
    
    // 解析获取到的歌词（个别歌词行包含未编码的 % 等字符时，decodeURIComponent 可能抛错）
    try {
        rem.lyric = parseLyric(str);
    } catch (e) {
        rem.lyric = '';
        lyricTip('歌词解析失败');
        return false;
    }
    
    if(rem.lyric === '') {
        lyricTip('没有歌词');
        return false;
    }
    
    lyricArea.html('');     // 清空歌词区域的内容
    lyricArea.scrollTop(0);    // 滚动到顶部
    
    rem.lastLyric = -1;
    
    // 显示全部歌词
    var i = 0;
    for(var k in rem.lyric){
        var txt = rem.lyric[k];
        if(!txt) txt = "&nbsp;";
        var li = $("<li data-no='"+i+"' class='lrc-item'>"+txt+"</li>");
        lyricArea.append(li);
        i++;
    }
}

// 强制刷新当前时间点的歌词
// 参数：当前播放时间（单位：秒）
function refreshLyric(time) {
    if(rem.lyric === '') return false;
    
    time = parseInt(time);  // 时间取整
    var i = 0;
    for(var k in rem.lyric){
        if(k >= time) break;
        i = k;      // 记录上一句的
    }
    
    scrollLyric(i);
}

// 滚动歌词到指定句
// 参数：当前播放时间（单位：秒）
function scrollLyric(time) {
    if(rem.lyric === '') return false;
    
    time = parseInt(time);  // 时间取整
    
    if(rem.lyric === undefined || rem.lyric[time] === undefined) return false;  // 当前时间点没有歌词
    
    if(rem.lastLyric == time) return true;  // 歌词没发生改变
    
    var i = 0;  // 获取当前歌词是在第几行
    for(var k in rem.lyric){
        if(k == time) break;
        i ++;
    }
    rem.lastLyric = time;  // 记录方便下次使用
    $(".lplaying").removeClass("lplaying");     // 移除其余句子的正在播放样式
    $(".lrc-item[data-no='" + i + "']").addClass("lplaying");    // 加上正在播放样式
    
    var scroll = (lyricArea.children().height() * i) - ($(".lyric").height() / 2); 
    lyricArea.stop().animate({scrollTop: scroll}, 1000);  // 平滑滚动到当前歌词位置(更改这个数值可以改变歌词滚动速度，单位：毫秒)
    
}

// 解析歌词
// 这一函数来自 https://github.com/TivonJJ/html5-music-player
// 参数：原始歌词文件
function parseLyric(lrc) {
    if(lrc === '') return '';
    var lyrics = lrc.split("\n");
    var lrcObj = {};
    for(var i=0;i<lyrics.length;i++){
        var lyric = lyrics[i];
        try {
            lyric = decodeURIComponent(lyrics[i]);
        } catch (e) {
            // 保持原始行，避免解析过程直接中断
            lyric = lyrics[i];
        }
        var timeReg = /\[\d*:\d*((\.|\:)\d*)*\]/g;
        var timeRegExpArr = lyric.match(timeReg);
        if(!timeRegExpArr)continue;
        var clause = lyric.replace(timeReg,'');
        for(var k = 0,h = timeRegExpArr.length;k < h;k++) {
            var t = timeRegExpArr[k];
            var min = Number(String(t.match(/\[\d*/i)).slice(1)),
                sec = Number(String(t.match(/\:\d*/i)).slice(1));
            var time = min * 60 + sec;
            lrcObj[time] = clause;
        }
    }
    return lrcObj;
}
