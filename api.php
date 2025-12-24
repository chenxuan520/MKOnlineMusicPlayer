<?php
/**************************************************
 * MKOnlinePlayer v2.4
 * 后台音乐数据抓取模块
 * 编写：mengkun(https://mkblog.cn)
 * 时间：2018-3-11
 * 特别感谢 @metowolf 提供的 Meting.php
 *************************************************/

/************ ↓↓↓↓↓ 如果网易云音乐歌曲获取失效，请将你的 COOKIE 放到这儿 ↓↓↓↓↓ ***************/
$netease_cookie = '';
/************ ↑↑↑↑↑ 如果网易云音乐歌曲获取失效，请将你的 COOKIE 放到这儿 ↑↑↑↑↑ ***************/
/**
* cookie 获取及使用方法见
* https://github.com/mengkunsoft/MKOnlineMusicPlayer/wiki/%E7%BD%91%E6%98%93%E4%BA%91%E9%9F%B3%E4%B9%90%E9%97%AE%E9%A2%98
*
* 更多相关问题可以查阅项目 wiki
* https://github.com/mengkunsoft/MKOnlineMusicPlayer/wiki
*
* 如果还有问题，可以提交 issues
* https://github.com/mengkunsoft/MKOnlineMusicPlayer/issues
**/


define('HTTPS', true);    // 如果您的网站启用了https，请将此项置为“true”，如果你的网站未启用 https，建议将此项设置为“false”
define('DEBUG', false);      // 是否开启调试模式，正常使用时请将此项置为“false”
define('JSONP', false);      // 是否开启JSONP模式，使用远程api时请开启
define('CACHE_PATH', 'cache/');     // 文件缓存目录,请确保该目录存在且有读写权限。如无需缓存，可将此行注释掉
define('TEMP_PATH', 'temp/');       // 临时文件目录，用于存放下载的歌曲，请确保该目录存在且有读写权限。

/*
 如果遇到程序不能正常运行，请开启调试模式，然后访问 http://你的网站/音乐播放器地址/api.php ，进入服务器运行环境检测。
 此外，开启调试模式后，程序将输出详细的运行错误信息，方便定位错误原因。

 因为调试模式下程序会输出服务器环境信息，为了您的服务器安全，正常使用时请务必关闭调试。
*/

/*****************************************************************************************************/
if(!defined('DEBUG') || DEBUG !== true) error_reporting(0); // 屏蔽服务器错误

require_once('plugns/Meting.php');
require_once('plugns/Download.php');

use Metowolf\Meting;
use Mxue\Download;

$source = getParam('source', 'netease');  // 歌曲源
$API = new Meting($source);
$DOWNLOAD = new Download($source);

$API->format(true); // 启用格式化功能

if($source == 'kugou' || $source == 'baidu' || $source == 'tencent') {
    define('NO_HTTPS', true);        // 酷狗、百度音乐和QQ源暂不支持 https
} elseif(($source == 'netease') && $netease_cookie) {
    $API->cookie($netease_cookie);    // 解决网易云 Cookie 失效
}

// 没有缓存文件夹则创建
if(defined('CACHE_PATH') && !is_dir(CACHE_PATH)) createFolders(CACHE_PATH);
// 没有临时文件夹则创建
if(defined('TEMP_PATH') && !is_dir(TEMP_PATH)) createFolders(TEMP_PATH);

$types = getParam('types');
switch($types)   // 根据请求的 Api，执行相应操作
{
    case 'url':   // 获取歌曲链接
        $id = getParam('id');  // 歌曲ID

        $data = $API->url($id);

        echojson($data);
        break;

    case 'pic':   // 获取封面链接
        $id = getParam('id');  // 歌曲ID

        $data = $API->pic($id);

        echojson($data);
        break;

    case 'lyric':       // 获取歌词
        $id = getParam('id');  // 歌曲ID

        if(defined('CACHE_PATH')) {
            $cache = CACHE_PATH.$source.'_'.$types.'_'.$id.'.json';

            if(file_exists($cache)) {   // 缓存存在，则读取缓存
                $data = file_get_contents($cache);
            } else {
                $data = $API->lyric($id);

                // 只缓存链接获取成功的歌曲
                if(isset($data) && isset(json_decode($data)->lyric)) {
                    file_put_contents($cache, $data);
                }
            }
        } else {
            $data = $API->lyric($id);
        }

        echojson($data);
        break;

    case 'userlist':    // 获取用户歌单列表
        $uid = getParam('uid');  // 用户ID

        if(defined('CACHE_PATH')) {
            $cache = CACHE_PATH.$source.'_'.$types.'_'.$uid.'.json';

            if(file_exists($cache)) {   // 缓存存在，则读取缓存
                $data = file_get_contents($cache);
            } else {
                $url= 'http://music.163.com/api/user/playlist/?offset=0&limit=1001&uid='.$uid;
                $data = file_get_contents($url);

                // 只缓存链接获取成功的用户列表
                if(isset($data) && isset(json_decode($data)->playlist)) {
                    file_put_contents($cache, $data);
                }
            }
        } else {
            $url= 'http://music.163.com/api/user/playlist/?offset=0&limit=1001&uid='.$uid;
            $data = file_get_contents($url);
        }

        echojson($data);
        break;

    case 'playlist':    // 获取歌单中的歌曲
        $id = getParam('id');  // 歌单ID

        if(defined('CACHE_PATH')) {
            $cache = CACHE_PATH.$source.'_'.$types.'_'.$id.'.json';

            if(file_exists($cache)) {   // 缓存存在，则读取缓存
                $data = file_get_contents($cache);
            } else {
                $data = $API->format(false)->playlist($id);

                // 只缓存链接获取成功的歌曲
                if(isset($data) && isset(json_decode($data)->playlist->tracks)) {
                    file_put_contents($cache, $data);
                }
            }
        } else {
            $data = $API->format(false)->playlist($id);
        }

        echojson($data);
        break;

    case 'search':  // 搜索歌曲
        $s = getParam('name');  // 歌名
        $limit = getParam('count', 20);  // 每页显示数量
        $pages = getParam('pages', 1);  // 页码

        if(defined('CACHE_PATH')) {
            $cache = CACHE_PATH.$source.'_'.$types.'_'.md5($s).'_'.$pages.'_'.$limit.'.json';

            if(file_exists($cache)) {   // 缓存存在，则读取缓存
                $data = file_get_contents($cache);
            } else {
                $data = $API->search($s, [
                    'page' => $pages,
                    'limit' => $limit
                ]);

                // 只缓存链接获取成功的歌曲
                if(isset($data) && json_decode($data)) {
                    file_put_contents($cache, $data);
                }
            }
        } else {
            $data = $API->search($s, [
                'page' => $pages,
                'limit' => $limit
            ]);
        }

        // 根据付费标识过滤歌曲
        $song_list = json_decode($data, true);
        if (is_array($song_list)) {
            $filtered_list = array_values(array_filter($song_list, function($song) {
                if (isset($song['source'])) {
                    switch ($song['source']) {
                        case 'netease':
                            // fee=1 是 VIP, fee=4 是付费专辑.
                            return isset($song['fee']) && $song['fee'] != 1 && $song['fee'] != 4;
                        case 'tencent':
                            // pay.pay_play=1 表示需要付费
                            return !(isset($song['pay']['pay_play']) && $song['pay']['pay_play'] == 1);
                        case 'kugou':
                            // privilege=0 是可播放
                            return !isset($song['privilege']) || $song['privilege'] == 0;
                        default:
                            // 对于其他未知来源，暂时默认放行
                            return true;
                    }
                }
                return true;
            }));
            $data = json_encode($filtered_list);
        }

        echojson($data);
        break;

    case 'comments':  // 获取评论
        $id = getParam('id');  // 歌曲id
        $limit = getParam('count', 50);  // 每页显示数量
        $pages = getParam('pages', 1);  // 页码

        if(defined('CACHE_PATH')) {
            $cache = CACHE_PATH.$source.'_'.$types.'_'.$id.'_'.$pages.'_'.$limit.'.json';

            if(file_exists($cache)) {   // 缓存存在，则读取缓存
                $data = file_get_contents($cache);
            } else {
                $data = $API->comments($id, [
                    'page' => $pages,
                    'limit' => $limit
                ]);

                // 只缓存链接获取成功的歌曲
                if(isset($data) && (isset(json_decode($data)->hot_comment) || isset(json_decode($data)->comment))) {
                    file_put_contents($cache, $data);
                }
            }
        } else {
            $data = $API->comments($id, [
                'page'  => $pages,
                'limit' => $limit
            ]);
        }

        echojson($data);
        break;

    case 'download':    // 下载歌曲
        $url = getParam('url');
        $name = getParam('name');
        $artist = getParam('artist');

        $data = $DOWNLOAD->download($url, $name, $artist);

        if (DEBUG) {
            // 在调试模式下，打印出文件路径和下载 URL
            $filepath_debug = dirname(dirname(__FILE__)).'/temp/'.$source.'/'.$name.$artist.'.mp3';
            $protocol_debug = ((isset($_SERVER['HTTPS']) && $_SERVER['HTTPS'] == 'on') || (isset($_SERVER['HTTP_X_FORWARDED_PROTO']) && $_SERVER['HTTP_X_FORWARDED_PROTO'] == 'https')) ? 'https://' : 'http://';
            $downpath_debug = $protocol_debug.$_SERVER['HTTP_HOST'].'/temp/'.$source.'/'.$name.$artist.'.mp3';
            error_log("Download filepath: " . $filepath_debug);
            error_log("Download URL: " . $downpath_debug);
            error_log("Download result: " . $data);
        }

        echojson($data);
        break;

    case 'cache':
        $minute = getParam('minute', 2);   // 删除几分钟之前的文件，默认为 2 分钟
        $target_path = getParam('target_path', CACHE_PATH); // 目标目录，默认为 CACHE_PATH
        $file_ext = getParam('file_ext', 'json'); // 文件扩展名，默认为 json

        date_default_timezone_set('Asia/Shanghai'); // 如果时区不同请自行设置时区

        // 确保目标目录存在且可读写
        if (!is_dir($target_path)) {
            echojson(json_encode(array('code' => 0, 'msg' => '目标目录不存在或不可访问。')));
            break;
        }

        $data = array();
        $iterator = new RecursiveIteratorIterator(
            new RecursiveDirectoryIterator($target_path, RecursiveDirectoryIterator::SKIP_DOTS),
            RecursiveIteratorIterator::CHILD_FIRST
        );

        foreach ($iterator as $fileinfo) {
            if ($fileinfo->isFile() && $fileinfo->getExtension() === $file_ext) {
                $filePath = $fileinfo->getPathname();
                if (strtotime('+'.$minute.' minute', $fileinfo->getMTime()) <= time()) {
                    $filetime = date('Y-m-d H:i:s', $fileinfo->getMTime());
                    if (unlink($filePath)) {
                        array_push($data, array(
                            'msg' => '删除成功。',
                            'time' => $filetime,
                            'file' => $filePath,
                        ));
                    } else {
                        array_push($data, array(
                            'msg' => '删除失败，请检查文件权限或其他问题。',
                            'time' => $filetime,
                            'file' => $filePath,
                        ));
                    }
                }
            }
        }

        echojson(json_encode($data));
        break;

    case 'collections':
        $action = getParam('action'); // add, remove, list, check, reorder
        $id = getParam('id');        // song id
        $source = getParam('source'); // song source
        $name = getParam('name');    // song name
        $artist = getParam('artist'); // song artist
        $album = getParam('album');  // song album
        $pic = getParam('pic');      // song pic
        $url_id = getParam('url_id'); // song url_id
        $pic_id = getParam('pic_id'); // song pic_id
        $lyric_id = getParam('lyric_id'); // song lyric_id

        $collectionFile = 'collections/collections.json';

        // Ensure collections directory exists with proper permissions for Docker environment
        if (!is_dir('collections')) {
            // Try to create directory with 0777 permissions to ensure it's writable in Docker
            if (!mkdir('collections', 0777, true)) {
                $response = array('success' => false, 'message' => '无法创建收藏目录，请确保项目根目录具有写入权限 (chmod 777)');
                echo json_encode($response);
                exit();
            }
        } elseif (!is_writable('collections')) {
            // If directory exists but is not writable, try to make it writable
            if (!chmod('collections', 0777)) {
                $response = array('success' => false, 'message' => '收藏目录不可写，请确保项目根目录具有写入权限 (chmod 777)');
                echo json_encode($response);
                exit();
            }
        }

        // Load existing collections
        $collections = array();
        if (file_exists($collectionFile)) {
            $collections = json_decode(file_get_contents($collectionFile), true);
            if (!$collections) {
                $collections = array();
            }
        }

        $response = array();

        switch($action) {
            case 'add':
                // Check if song already exists in collections
                $exists = false;
                foreach($collections as $key => $song) {
                    if ($song['id'] == $id && $song['source'] == $source) {
                        $exists = true;
                        break;
                    }
                }

                if (!$exists) {
                    $newSong = array(
                        'id' => $id,
                        'name' => $name,
                        'artist' => $artist,
                        'album' => $album,
                        'source' => $source,
                        'url_id' => $url_id,
                        'pic_id' => $pic_id,
                        'lyric_id' => $lyric_id,
                        'pic' => $pic
                    );
                    array_push($collections, $newSong);
                    file_put_contents($collectionFile, json_encode($collections));
                    $response = array('success' => true, 'message' => '歌曲已收藏');
                } else {
                    $response = array('success' => false, 'message' => '歌曲已收藏');
                }
                break;

            case 'remove':
                $songRemoved = false;
                foreach($collections as $key => $song) {
                    if ($song['id'] == $id && $song['source'] == $source) {
                        array_splice($collections, $key, 1);
                        $songRemoved = true;
                        break;
                    }
                }

                if ($songRemoved) {
                    file_put_contents($collectionFile, json_encode($collections));
                    $response = array('success' => true, 'message' => '歌曲已取消收藏');
                } else {
                    $response = array('success' => false, 'message' => '歌曲不在收藏列表中');
                }
                break;

            case 'list':
                $response = array('success' => true, 'collections' => $collections);
                break;

            case 'check':
                $isCollected = false;
                foreach($collections as $song) {
                    if ($song['id'] == $id && $song['source'] == $source) {
                        $isCollected = true;
                        break;
                    }
                }
                $response = array('success' => true, 'collected' => $isCollected);
                break;

            case 'reorder':
                // 重新排序收藏列表
                $reorderedCollections = getParam('collections', null);
                if ($reorderedCollections !== null) {
                    $reorderedCollections = json_decode($reorderedCollections, true);
                    if (is_array($reorderedCollections)) {
                        // 验证传入的收藏列表与现有收藏列表包含相同的歌曲
                        $existingIds = array();
                        foreach($collections as $song) {
                            $existingIds[] = $song['id'] . '_' . $song['source'];
                        }

                        $newIds = array();
                        foreach($reorderedCollections as $song) {
                            $newIds[] = $song['id'] . '_' . $song['source'];
                        }

                        // 检查两个数组是否包含相同的歌曲
                        sort($existingIds);
                        sort($newIds);

                        if ($existingIds === $newIds) {
                            // 保存重新排序后的收藏列表
                            file_put_contents($collectionFile, json_encode($reorderedCollections));
                            $response = array('success' => true, 'message' => '收藏列表排序已保存');
                        } else {
                            $response = array('success' => false, 'message' => '排序数据不一致');
                        }
                    } else {
                        $response = array('success' => false, 'message' => '排序数据格式错误');
                    }
                } else {
                    $response = array('success' => false, 'message' => '缺少排序数据');
                }
                break;

            default:
                $response = array('success' => false, 'message' => 'Invalid action');
        }

        echojson(json_encode($response));
        break;

    default:
        echo '<!doctype html><html><head><meta charset="utf-8"><title>信息</title><style>* {font-family: microsoft yahei}</style></head><body> <h2>MKOnlinePlayer</h2><h3>Github: https://github.com/mengkunsoft/MKOnlineMusicPlayer</h3><br>';
        if(!defined('DEBUG') || DEBUG !== true) {   // 非调试模式
            echo '<p>Api 调试模式已关闭</p>';
        } else {
            echo '<p><font color="red">您已开启 Api 调试功能，正常使用时请在 api.php 中关闭该选项！</font></p><br>';

            echo '<p>PHP 版本：'.phpversion().' （本程序要求 PHP 5.4+）</p><br>';

            echo '<p>服务器函数检查</p>';
            echo '<p>curl_exec: '.checkfunc('curl_exec',true).' （用于获取音乐数据）</p>';
            echo '<p>file_get_contents: '.checkfunc('file_get_contents',true).' （用于获取音乐数据）</p>';
            echo '<p>json_decode: '.checkfunc('json_decode',true).' （用于后台数据格式化）</p>';
            echo '<p>hex2bin: '.checkfunc('hex2bin',true).' （用于数据解析）</p>';
            echo '<p>openssl_encrypt: '.checkfunc('openssl_encrypt',true).' （用于数据解析）</p>';
        }

        echo '</body></html>';
}

/**
 * 创建多层文件夹
 * @param $dir 路径
 */
function createFolders($dir) {
    return is_dir($dir) or (createFolders(dirname($dir)) and mkdir($dir, 0777));
}

/**
 * 检测服务器函数支持情况
 * @param $f 函数名
 * @param $m 是否为必须函数
 * @return
 */
function checkfunc($f,$m = false) {
	if (function_exists($f)) {
		return '<font color="green">可用</font>';
	} else {
		if ($m == false) {
			return '<font color="black">不支持</font>';
		} else {
			return '<font color="red">不支持</font>';
		}
	}
}

/**
 * 获取GET或POST过来的参数
 * @param $key 键值
 * @param $default 默认值
 * @return 获取到的内容（没有则为默认值）
 */
function getParam($key, $default='')
{
    return trim($key && is_string($key) ? (isset($_POST[$key]) ? $_POST[$key] : (isset($_GET[$key]) ? $_GET[$key] : $default)) : $default);
}

/**
 * 输出一个json或jsonp格式的内容
 * @param $data 数组内容
 */
function echojson($data)    //json和jsonp通用
{
    header('Content-type: application/json');
    $callback = getParam('callback');

    if(defined('HTTPS') && HTTPS === true && !defined('NO_HTTPS')) {    // 替换链接为 https
        $data = str_replace('http:\/\/', 'https:\/\/', $data);
        $data = str_replace('http://', 'https://', $data);
    }

    if(defined('JSONP') && JSONP === true && $callback) //输出jsonp格式
    {
        die(htmlspecialchars($callback).'('.$data.')');
    } else {
        die($data);
    }
}
