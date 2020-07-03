<%@ page import="java.util.logging.Logger" %><%--
  Created by IntelliJ IDEA.
  User: Jezer
  Date: 2020/5/6
  Time: 16:09
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%
    String path = request.getContextPath();
    System.out.println(path);
    String addr = request.getRemoteAddr();
    System.out.println(addr);
    String basePath = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + path + "/";
    System.out.println(basePath);
%>
<!DOCTYPE html>
<html>
<head>
    <%--    这句代码的作用是将整个页面的根路径设置为项目路径。--%>
    <base href="<%=basePath%>">
    <meta charset="utf-8">
    <title>Face Recognition Demo</title>
</head>

<style type="text/css">
    .div1 {
        width: 50px;
        display: inline-block;
    }

    .div2 {
        width: 200px;
        display: inline-block;
    }
</style>

<body>
<h2>Face Recognition Demo</h2>

<div id="form-div">
    <form id="profileFrom" onsubmit="return postProfileData()">
        <%--姓名输入栏位--%>
        <div style="margin-top: 5px;">
            <div class="div1">
                姓名：
            </div>
            <div class="div2">
                <input name="userName" type="text" id="nameInput" tabindex="1"
                       size="15" value="" onclick="initialStatus();" onchange="initialButton();"
                       placeholder=""/>
            </div>
        </div>
        <%--id输入栏位--%>
        <div style="margin-top: 5px;">
            <div class="div1">
                ID:
            </div>
            <div class="div2">
                <input name="id" type="text" id="idInput"
                       tabindex="2" size="15" value="" onclick="initialStatus();" onchange="initialButton();"/>
            </div>
        </div>

        <div style="display: inline-block;margin-top: 5px;">
            <input id="register" type="submit" value="注册" onclick="check(this);initialStatus();" disabled>
            <input id="login" type="submit" value="登录" onclick="check(this);initialStatus();" disabled>
            <input type="reset" value="重置" onclick="initialStatus();">
        </div>
        <input id="btn_type" name="btnType" style="display: none;"/>
        <input id="step" name="step" style="display: none;" value="step1"/>
    </form>

</div>

<div style="margin-top: 5px;">
    <div class="control" style="display: inline;">
        <button id="startAndStop" disabled>开始</button>
        <button id="login_with_face">人脸登录</button>
    </div>
    <div id="tips" style="display: inline"></div>
</div>

<p class="err" id="errorMessage"></p>
<div>
    <table cellpadding="0" cellspacing="0" width="0" border="0">
        <tr>
            <td>
                <div style="text-align: center;font-size: 20px;">
                    <p id="status"></p>
                </div>
                <div>
                    <video id="videoInput" width=320 height=240 style="display: none"></video>
                    <canvas id="videoCopy" width=320 height=240></canvas>
                </div>
            </td>
            <td>
                <canvas id="frontCanvasOutput" width=160 height=160></canvas>
            </td>
            <td>
                <canvas id="leftCanvasOutput" width=160 height=160></canvas>
            </td>
            <td>
                <canvas id="RightCanvasOutput" width=160 height=160></canvas>
            </td>
        </tr>
    </table>
</div>
<%--method="post" action="MyFirstServlet"--%>
<form id="passForm"
      onsubmit="return PostImgData()" style="display: none;">
    <input name="step" value="step2">
    <input id="frontface" name="frontface">
    <input id="leftface" name="leftface">
    <input id="rightface" name="rightface">
    <input id="upload" type="submit">
</form>

<%--<script src="js/adapter-5.0.4.js" type="text/javascript"></script>--%>
<script src="js/utils.js" type="text/javascript"></script>
<script src="js/jquery-3.5.1.js" type="text/javascript"></script>
<script type="text/javascript">
    let utils = new Utils('errorMessage');
    let streaming = false;
    let videoInput = document.getElementById('videoInput');
    let startAndStop = document.getElementById('startAndStop');
    let faceLoginBtn = document.getElementById('login_with_face')
    let video = document.getElementById('videoInput');
    let status = document.getElementById('status');
    let tip = document.getElementById("tips");

    let nameInput = document.getElementById("nameInput");
    let idInput = document.getElementById("idInput");
    let loginBtn = document.getElementById("login");
    let regBtn = document.getElementById("register");

    let frontCanvas = document.getElementById('frontCanvasOutput');
    let frontCtx = frontCanvas.getContext('2d');
    let leftCanvas = document.getElementById('leftCanvasOutput');
    let leftCtx = leftCanvas.getContext('2d');
    let rightCanvas = document.getElementById('RightCanvasOutput');
    let rightCtx = rightCanvas.getContext('2d');
    let videoCtx = document.getElementById("videoCopy").getContext('2d');
    function showVideo() {
        videoCtx.drawImage(video, 0, 0, 320, 240);
        videoCtx.beginPath();
        //设置弧线的颜色为蓝色
        videoCtx.strokeStyle = "white";
        var circle = {
            x: 160,    //圆心的x轴坐标值
            y: 120,    //圆心的y轴坐标值
            r: 100        //圆的半径
        };
        //沿着坐标点(100,100)为圆心、半径为50px的圆的顺时针方向绘制弧线
        videoCtx.arc(circle.x, circle.y, circle.r, 0, 2 * Math.PI, false);
        //按照指定的路径绘制弧线
        videoCtx.stroke();
    }

    function check(button) {
        document.getElementById("btn_type").value = button.id;
    }

    function setButton() {
        if (nameInput.value != "" && idInput.value != "") {
            loginBtn.removeAttribute("disabled");
            regBtn.removeAttribute('disabled');
            setTimeout(setButton, 10);
        } else {
            loginBtn.disabled = true;
            regBtn.disabled = true;
            setTimeout(setButton, 10);
        }

    }

    setButton();

    /*传递个人资料*/
    function postProfileData() {
        $.ajax({
            type: "POST",
            url: "FaceRecognitionServlet",
            data: $('#profileFrom').serialize(),
            success: function (status) {
                console.log("step1 status: " + status);
                if (status == 0) {
                    /*0:登录信息输入错误*/
                    tip.innerText = "信息错误，请重新输入！";
                } else if (status == 1) {
                    /*1:登录信息输入正确*/
                    tip.innerText = "信息正确，点击开始进行人脸识别登录！";
                    startAndStop.removeAttribute('disabled');
                } else if (status == 4) {
                    /*4:查无此人，可注册*/
                    tip.innerText = "点击开始进行人脸识别！";
                    startAndStop.removeAttribute('disabled');
                } else if (status == 5) {
                    /*5:查到此人，请登录*/
                    tip.innerText = "已有此人，点击开始进行人脸识别登录！";
                    startAndStop.removeAttribute('disabled');
                } else if (status == 6) {
                    /*5:查到此人，请登录*/
                    tip.innerText = "id重复，请输入正确的id！";
                }
            }
        });
        return false;
    }

    function PostImgData() {
        status.innerHTML = "...检测中...";
        $.ajax({
            type: "POST",
            url: "FaceRecognitionServlet",
            data: $('#passForm').serialize() + "&" + $('#profileFrom').serialize(),
            success: function (msg) {
                onVideoStopped();
                console.log("step2 status: " + msg);
                if (msg == 0) {
                    status.style.color = "red";
                    status.innerHTML = "认证失败";
                } else if (msg == 1) {
                    status.style.color = "green";
                    status.innerHTML = "认证成功";
                } else if (msg == 2) {
                    status.style.color = "green";
                    status.innerHTML = "注册成功";
                } else {
                    console.log(msg);
                    var json = eval("(" + msg + ")")[0];
                    var code = json.code;
                    console.log(json);
                    console.log(status);
                    if (code == 3){
                        var id = json.id;
                        var name = json.name;
                        status.innerHTML = "你好 "+id+"-"+name+"!";
                    }
                    else{
                        status.innerHTML = "还没注册?\n输入姓名、id去注册吧！";
                    }
                }
            }
        });
        return false;
    }

    function myPost() {
        status.innerHTML = "...检测中...";
        document.getElementById("frontface").value = frontArr;
        document.getElementById("leftface").value = leftArr;
        document.getElementById("rightface").value = rightArr;
        frontArr = null;
        leftArr = null;
        rightArr = null;
        document.getElementById("upload").click();
    }

    let src;
    let dst;
    let gray;
    let cap;
    let frontfaces;
    let leftfaces;
    let rightfaces;

    let frontArr;
    let leftArr;
    let rightArr;

    let front_classifier;
    let left_classifier;

    const FPS = 30;
    front_number = 5;
    left_number = 3;
    right_number = 3;

    function processStart() {
        src = new cv.Mat(video.height, video.width, cv.CV_8UC4);
        dst = new cv.Mat(video.height, video.width, cv.CV_8UC4);
        gray = new cv.Mat();
        cap = new cv.VideoCapture(video);
        frontfaces = new cv.RectVector();
        leftfaces = new cv.RectVector();
        rightfaces = new cv.RectVector();

        frontArr = new Array(5);
        leftArr = new Array(3);
        rightArr = new Array(3);

        front_classifier = new cv.CascadeClassifier();
        left_classifier = new cv.CascadeClassifier();

        let a = front_classifier.load('front.xml');
        let b= left_classifier.load('profile.xml');
        console.log(a,b)
        setTimeout(processVideo, 40);
    }
    function processVideo() {
        try {
            if (!streaming) {
                console.log("not streaming");
                // clean and stop.
                src.delete();
                dst.delete();
                gray.delete();

                front_number = 5;
                left_number = 3;
                right_number = 3;

                leftfaces.delete();
                rightfaces.delete();
                frontfaces.delete();

                front_classifier.delete();
                left_classifier.delete();
                return;
            }
            showVideo();
            let begin = Date.now();
            // start processing.
            cap.read(src);
            src.copyTo(dst);
            cv.cvtColor(dst, gray, cv.COLOR_RGBA2GRAY, 0);
            console.log(gray)

            // detect faces.
            let msize = new cv.Size(video.height / 2, video.height / 2);

            front_classifier.detectMultiScale(gray, frontfaces, 1.3, 3, 0, msize);
            left_classifier.detectMultiScale(gray, leftfaces, 1.3, 3, 0, msize);
            console.log(front_classifier)
            if (frontfaces.size()>0) console.log(frontfaces)
            // 检测正脸
            if (front_number != 0 && left_number != 0 && right_number != 0) {
                status.innerHTML = "目视前方";
                for (let i = 0; i < frontfaces.size(); ++i) {
                    let face = frontfaces.get(i);
                    frontCtx.clearRect(0, 0, 0, 0);
                    frontCanvas.width = 160;
                    frontCanvas.height = 160;
                    frontCtx.drawImage(video, face.x, face.y, face.width, face.height, 0, 0, 160, 160);

                    /*前端传递rgb时的代码*/
                    // let frame = frontCtx.getImageData(0, 0, 160, 160);
                    // let image = new Array(76800);
                    // for (let k = 0; k < 25600; k++) {
                    //     for (let c = 0; c < 3; c++) {
                    //         image[k * 3 + c] = frame.data[k * 4 + c];
                    //     }
                    // }
                    // frontArr[front_number - 1] = image;


                    /*前端传递base64时的代码*/
                    var frontData = frontCanvas.toDataURL('image/jpeg', 1.0);
                    frontArr += frontData;
                    console.log(frontData)
                    console.log(frontArr)
                    front_number--;
                }

            }
            // 检测左脸
            else if (front_number == 0 && left_number != 0 && right_number != 0) {
                status.innerHTML = "向右转头";
                if (frontfaces.size() == 0) {
                    for (let i = 0; i < leftfaces.size(); ++i) {
                        leftCtx.clearRect(0, 0, 0, 0);
                        let face = leftfaces.get(i);
                        leftCanvas.width = 160;
                        leftCanvas.height = 160;
                        leftCtx.drawImage(video, face.x, face.y, face.width, face.height, 0, 0, 160, 160);

                        /*前端传递rgb时的代码*/
                        // let frame = leftCtx.getImageData(0, 0, 160, 160);
                        // let image = new Array(76800);
                        // for (let k = 0; k < 25600; k++) {
                        //     for (let c = 0; c < 3; c++) {
                        //         image[k * 3 + c] = frame.data[k * 4 + c];
                        //     }
                        // }
                        // leftArr[left_number - 1] = image;

                        /*前端传递base64时的代码*/
                        var leftData = leftCanvas.toDataURL('image/jpeg', 1.0);
                        leftArr += leftData;

                        left_number--;
                    }
                }
            }
            // 检测右脸
            else if (front_number == 0 && left_number == 0 && right_number != 0) {
                status.innerHTML = "向左转头";
                if (frontfaces.size() == 0 && leftfaces.size() == 0) {
                    cv.flip(gray, gray, 1);
                    left_classifier.detectMultiScale(gray, rightfaces, 1.3, 3, 0, msize);
                    for (let i = 0; i < rightfaces.size(); ++i) {
                        rightCtx.clearRect(0, 0, 0, 0);
                        let face = rightfaces.get(i);
                        rightCanvas.width = 160;
                        rightCanvas.height = 160;
                        rightCtx.drawImage(video, video.width - face.width - face.x, face.y, face.width, face.height, 0, 0, 160, 160);

                        /*前端传递rgb时的代码*/
                        // let frame = rightCtx.getImageData(0, 0, 160, 160);
                        // let image = new Array(76800);
                        // for (let k = 0; k < 25600; k++) {
                        //     for (let c = 0; c < 3; c++) {
                        //         image[k * 3 + c] = frame.data[k * 4 + c];
                        //     }
                        // }
                        // rightArr[right_number - 1] = image;
                        //


                        /*前端传递base64时的代码*/
                        var rightData = rightCanvas.toDataURL('image/jpeg', 1.0);
                        rightArr += rightData;

                        right_number--;
                    }
                }
            } else if ((front_number || left_number || right_number) == 0) {
                status.innerHTML = "...检测中...";
                front_number = 5;
                left_number = 3;
                right_number = 3;
                setTimeout(myPost, 5);
                return;
            }
            // schedule the next one.
            console.log("processVideo");
            setTimeout(processVideo, 10);
        } catch (err) {
            utils.printError(err);
        }
    };

    function initialStatus() {
        status.innerText = '';
        tip.innerText = '';
        status.style.color = "black";
    }

    function initialButton() {
        loginBtn.disabled = true;
        regBtn.disabled = true;
        startAndStop.disabled = true;
    }

    faceLoginBtn.addEventListener('click', () => {
        console.log("faceLoginBtn")
        initialStatus();
        document.getElementById("btn_type").value = "faceLogin";
        if (!streaming) {
            tip.innerHTML = '';
            utils.clearError();
            startAndStop.disabled = true;
            utils.startCamera('qvga', onVideoStarted, 'videoInput');
        }
        /*开始状态*/
        else {
            utils.stopCamera();
            onVideoStopped();
        }
    });

    startAndStop.addEventListener('click', () => {
        /*未开始状态*/
        if (!streaming) {
            tip.innerHTML = '';
            utils.clearError();
            startAndStop.disabled = true;
            utils.startCamera('qvga', onVideoStarted, 'videoInput');
        }
        /*开始状态*/
        else {
            utils.stopCamera();
            onVideoStopped();
        }
    });

    function onVideoStarted() {
        streaming = true;
        startAndStop.innerText = '停止';
        startAndStop.disabled = false;
        videoInput.width = videoInput.videoWidth;
        videoInput.height = videoInput.videoHeight;
        processStart();
    }

    function onVideoStopped() {
        streaming = false;
        frontCtx.clearRect(0, 0, 160, 160);
        leftCtx.clearRect(0, 0, 160, 160);
        rightCtx.clearRect(0, 0, 160, 160);
        videoCtx.clearRect(0, 0, 320, 240);

        startAndStop.disabled = true;
        startAndStop.innerText = '开始';
        initialStatus();
        utils.stopCamera();
    }

    utils.loadOpenCv(() => {
        let profileCascadeFile = 'js/haarcascade_profileface.xml';
        let faceCascadeFile = 'js/haarcascade_frontalface_default.xml';
        utils.createFileFromUrl('profile.xml', profileCascadeFile, () => {
            utils.createFileFromUrl('front.xml', faceCascadeFile, () => {
                // startAndStop.removeAttribute('disabled');
            });
        });
    });

</script>
</body>
</html>
