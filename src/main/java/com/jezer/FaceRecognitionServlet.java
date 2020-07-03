package com.jezer;

import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Base64;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.csvreader.CsvReader;
import com.csvreader.CsvWriter;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;
import org.tensorflow.Tensor;

/**
 * @author Jezer
 */
public class FaceRecognitionServlet extends HttpServlet {
    private Session tfSession;

    /*登录的状态 0:登录信息输入错误  1:登录信息输入正确  2:登录成功  3:登录失败*/
    private int loginStatus = 0;
    /*注册的状态 4:查无此人，可注册  5:查到此人，请登录  6:id重复 */
    private int regStatus = 4;

    private float[][] featuresStored = new float[3][512];

    @Override
    public void init() throws ServletException {
        super.init();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        System.out.println(df.format(System.currentTimeMillis()));
        long startTime = System.currentTimeMillis();
        String path = getServletContext().getRealPath("/");
        SavedModelBundle model = SavedModelBundle.load(path + "model", "TAG");
        tfSession = model.session();
        float[][][][] warmUp = new float[1][160][160][3];
        Tensor inputX = Tensor.create(warmUp);
        Tensor inputY = Tensor.create(false);
        tfSession.runner().feed("image_batch", inputX)
                .feed("phase_train", inputY).fetch("embeddings").run().get(0);
        long endTime = System.currentTimeMillis();
        System.out.println("init() takes " + (endTime - startTime) + "ms\n");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String data = request.getParameter("pic");
        response.getWriter().write(data);
        System.out.println(data);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        System.out.println("=================");
        System.out.println("remote ip addr: " + request.getRemoteAddr());
        String step = request.getParameter("step");
        String name = request.getParameter("userName");
        String id = request.getParameter("id");
        String btnType = request.getParameter("btnType");
        String basePath = request.getSession().getServletContext().getRealPath("");
        System.out.println(basePath);
        System.out.println("-----------------");
        System.out.println(step + " " + name + " " + id + " " + btnType + " ");


        /*注册 登录按钮的步骤*/
        if ("step1".equals(step)) {
            switch (btnType) {
                case "login":
                    /*读取此人的人脸特征值*/
                    loginStatus = 0;
                    featuresStored = readCSV(basePath + "/test.csv", name, id);

                    if (loginStatus == 0) {
                        System.out.println("登录信息输入错误");
                    } else if (loginStatus == 1) {
                        System.out.println("登录信息输入正确");
                    }
                    /*返回登录状态*/
                    response.getWriter().println(loginStatus);
                    break;
                case "register":
                    /*检测csv文件*/
                    checkCSV(basePath + "/test.csv", name, id);
                    if (regStatus == 4) {
                        System.out.println("查无此人，可注册");
                    } else if (regStatus == 5) {
                        System.out.println("查到此人，请登录");
                    } else if (regStatus == 6) {
                        System.out.println("id重复");
                    }
                    /*返回注册状态*/
                    response.getWriter().println(regStatus);
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + btnType);
            }
        }
        /*调用模型的步骤*/
        else if ("step2".equals(step)) {
            /*前端传递rgb时的代码*/
            /*String[] frontTest = request.getParameter("frontface").split(",");
            String[] leftTest = request.getParameter("leftface").split(",");
            String[] rightTest = request.getParameter("rightface").split(",");*/

            try {
                /*前端传递base64时的代码*/
                String[] frontTest = request.getParameter("frontface").split("data:image/jpeg;base64,");
                String[] leftTest = request.getParameter("leftface").split("data:image/jpeg;base64,");
                String[] rightTest = request.getParameter("rightface").split("data:image/jpeg;base64,");

                frontTest[0] = "front";
                leftTest[0] = "left";
                rightTest[0] = "right";

                String picPath = basePath + "facePic";
                File file = new File(picPath);
                if (!file.exists()) {
                    file.mkdir();
                }
                generateImage(frontTest, picPath);
                generateImage(leftTest, picPath);
                generateImage(rightTest, picPath);

                /*人脸特征平均值*/
                float[][] featuresInput = new float[3][512];
                /*计算人脸特征平均值*/
                long a = System.currentTimeMillis();

                /*前端传递rgb时的代码*/
                /*System.out.println("cal avg front");
                featuresInput[0] = calAvgFeature(frontTest, 5);
                System.out.println("cal avg left");
                featuresInput[1] = calAvgFeature(leftTest, 3);
                System.out.println("cal avg right");
                featuresInput[2] = calAvgFeature(rightTest, 3);*/

                /*前端传递base64时的代码*/
                System.out.println("cal avg front");
                featuresInput[0] = calAvgFeature(picPath + "/front");
                System.out.println("cal avg left");
                featuresInput[1] = calAvgFeature(picPath + "/left");
                System.out.println("cal avg right");
                featuresInput[2] = calAvgFeature(picPath + "/right");

                System.out.println("cal avg feature time: " + (System.currentTimeMillis() - a));
                /*step2 */
                if ("register".equals(btnType) && regStatus == 4) {
                    writeCSV(basePath + "/test.csv", name, id, featuresInput[0], featuresInput[1], featuresInput[2]);
                    /*注册成功*/
                    response.getWriter().println("2");
                } else if ("login".equals(btnType)) {
                    /*登录*/
                    /*点击注册时有此人 加载此人人脸数据*/
                    if (regStatus == 5) {
                        featuresStored = readCSV(basePath + "/test.csv", name, id);
                    }
                    float distance = calDist(featuresStored, featuresInput);
                    System.out.println(distance);
                    if (distance <= 0.8) {
                        /*同一个人*/
                        response.getWriter().println("1");
                        updateCSV(basePath, name, id, featuresInput, featuresStored);
                    } else {
                        /*不同人*/
                        response.getWriter().println("0");
                    }
                } else {
                    assert "faceLogin".equals(btnType);
                    String[] result = loginWithFace(basePath + "/test.csv", featuresInput);
                    JSONArray jsonArray = new JSONArray();
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("code", result[0]);
                    jsonObject.put("id", result[1]);
                    jsonObject.put("name", result[2]);
                    jsonArray.add(jsonObject);
                    String jsonString = jsonArray.toJSONString();
                    System.out.println(jsonString);
                    response.getWriter().println(jsonString);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            long endTime = System.currentTimeMillis();
            System.out.println("total running time:" + (endTime - startTime) + "ms\n");

        }
    }

    private String[] loginWithFace(String path, float[][] featureIn) {
        String[] result = new String[3];
        result[0] = "-1";
        assert new File(path).exists();
        try {
            CsvReader reader = new CsvReader(path, ',', Charset.forName("UTF-8"));
            // 跳过表头 如果需要表头的话，这句可以忽略
            reader.readHeaders();
            // 逐行读入除表头的数据
            long a = System.currentTimeMillis();
            while (reader.readRecord()) {
                float[][] features = new float[3][512];
//                long b = System.nanoTime();
                features[0] = stringToFloatArr(reader.getValues()[2]);
                features[1] = stringToFloatArr(reader.getValues()[3]);
                features[2] = stringToFloatArr(reader.getValues()[4]);
//                System.out.println("read csv, total spent: " + (System.nanoTime() - b) + "ns");
                float distance = calDist(featureIn, features);
//                System.out.println("1 row total spent: " + (System.nanoTime() - b) + "ns");
                System.out.println("distance: " + distance);
                if (distance <= 0.8) {
//                    System.out.println("find person, spent: " + (System.currentTimeMillis() - a) + "ms");
                    /*id*/
                    result[0] = "3";
                    result[1] = reader.getValues()[0];
                    /*name*/
                    result[2] = reader.getValues()[1];
                    return result;
                }
            }
            System.out.println("can't find person, spent: " + (System.currentTimeMillis() - a) + "ms\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    private void updateCSV(String path, String name, String id, float[][] feature1, float[][] feature2) {

        float[][] newFeature = new float[3][512];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 512; j++) {
                newFeature[i][j] += feature1[i][j] * 0.9 + feature2[i][j] * 0.1;
            }
        }

        try {
            String newCSV = path + "/temp.csv";
            String oldCSV = path + "/test.csv";

            File csv = new File(newCSV);
            csv.createNewFile();
            Writer fileWriter = new FileWriter(csv, true);
            CsvWriter csvWriter = new CsvWriter(fileWriter, ',');
            /*写入表头*/
            String[] csvHeaders = {"编号", "姓名", "frontFeature", "leftFeature", "rightFeature"};
            csvWriter.writeRecord(csvHeaders);

            CsvReader reader = new CsvReader(oldCSV, ',', Charset.forName("UTF-8"));
            // 跳过表头 如果需要表头的话，这句可以忽略
            reader.readHeaders();
            String[] csvContent;
            // 读取旧的文件
            while (reader.readRecord()) {
                if (id.equals(reader.getValues()[0]) && name.equals(reader.getValues()[1])) {
                    csvContent = new String[]{reader.getValues()[0], reader.getValues()[1],
                            Arrays.toString(newFeature[0]).substring(1, Arrays.toString(newFeature[0]).length() - 1),
                            Arrays.toString(newFeature[1]).substring(1, Arrays.toString(newFeature[1]).length() - 1),
                            Arrays.toString(newFeature[2]).substring(1, Arrays.toString(newFeature[2]).length() - 1)};
                } else {
                    csvContent = new String[]{reader.getValues()[0], reader.getValues()[1],
                            reader.getValues()[2], reader.getValues()[3],
                            reader.getValues()[4]};

                }
                csvWriter.writeRecord(csvContent);
            }
            csvWriter.close();
            reader.close();
            boolean delete = new File(oldCSV).delete();
            boolean rename = new File(newCSV).renameTo(new File(oldCSV));
            System.out.println(delete + " " + rename);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*前端传递base64时的代码*/
    public float[] calAvgFeature(String folderPath) throws Exception {
        /**
         * @description: 计算文件下所有人脸图片的平均特征值
         * @param folderPath: 单侧人脸图片
         * @return: float[]: 平均特征值
         * @author: JiaZe
         * @date: 2020/5/11
         */
//        long a = System.currentTimeMillis();
        float[] avg = new float[512];

        File file = new File(folderPath);
        if (!file.exists()) {
            file.mkdir();
        }
        String[] fileName = file.list();
        int j = 0;
        assert fileName != null;
        for (String image : fileName) {
            String imgPath = folderPath + "/" + image;
            float[] tmp = calFeature(imgPath);
            for (int i = 0; i < 512; i++) {
                avg[i] += tmp[i];
                if (j == fileName.length - 1) {
                    avg[i] = avg[i] / fileName.length;
                }
            }
            j++;
        }
//        System.out.println("calAvgFeature: " + (System.currentTimeMillis() - a) + "ms\n");
        return avg;
    }

    private float[] calFeature(String imgPath) throws Exception {
//        long a = System.currentTimeMillis();
        /**
         * @description: 计算人脸图片的特征值
         * @param imgPath: 图片路径
         * @return: float[]: 特征值
         * @author: JiaZe
         * @date: 2020/5/11
         */
        Tensor inputX = Tensor.create(getImagePixel(imgPath));
        Tensor inputY = Tensor.create(false);
        Tensor result = tfSession.runner().feed("image_batch", inputX)
                .feed("phase_train", inputY).fetch("embeddings").run().get(0);
        float[][] ans = new float[1][512];
        result.copyTo(ans);
//        System.out.println("spend time: " + (System.currentTimeMillis() - a) + "ms");
        return ans[0];
    }

    /*返回带batch的预处理图片 可直接输入模型*/
    private float[][][][] preWhiten(float[][][] image) {
        long a = System.currentTimeMillis();
        /**
         * @description: 预处理图片
         * @param image: 160*160*3 的图片
         * @return: float[][][][] 1*160*160*3的图片，
         * @author: JiaZe
         * @date: 2020/5/14
         */
        int width = 160;
        int height = 160;
        int channel = 3;
        int length = 160 * 160 * 3;
        float[][][][] output = new float[1][160][160][3];
        float total = 0;
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                for (int c = 0; c < 3; c++) {
                    total += image[i][j][c];
                }
            }
        }
        double mean = total / length;

        double std = 0;
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                for (int k = 0; k < channel; k++) {
                    std += Math.pow(image[i][j][k] - mean, 2);
                }
            }
        }
        /*标准差*/
        std = std / length;
        std = Math.sqrt((double) std);
        double std_adj = Math.max(std, 1 / Math.sqrt(length));

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                for (int k = 0; k < channel; k++) {
                    /*图片数据归一化*/
                    output[0][i][j][k] = (float) ((image[i][j][k] - mean) / std_adj);
                }
            }
        }
        System.out.println("preWhiten: " + (System.currentTimeMillis() - a) + "ms\n\n");
        return output;
    }

    private float calDist(float[][] feature1, float[][] feature2) {
//        long a = System.nanoTime();
        double[] distances = new double[3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 512; j++) {
                distances[i] += Math.pow(feature1[i][j] - feature2[i][j], 2);
            }
            distances[i] = Math.sqrt(distances[i]);
        }
//        System.out.println("calDist: " + (System.nanoTime() - a) + "ns");
        return (float) (distances[0] * 0.7 + distances[1] * 0.15 + distances[2] * 0.15);
    }

    private void writeCSV(String path, String name, String id, float[] f, float[] l, float[] r) {
        long a = System.currentTimeMillis();
        try {
            // 创建CSV写对象 例如:CsvWriter(文件路径，分隔符，编码格式);
            File csv = new File(path);
            Writer fileWriter = new FileWriter(csv, true);
            CsvWriter csvWriter = new CsvWriter(fileWriter, ',');
            CsvReader reader = new CsvReader(path, ',', Charset.forName("UTF-8"));
            if (!reader.readHeaders()) {
                // 写表头
                String[] csvHeaders = {"编号", "姓名", "frontFeature", "leftFeature", "rightFeature"};
                csvWriter.writeRecord(csvHeaders);
            }
            // 写内容
            String[] csvContent = {id, name, Arrays.toString(f).substring(1, Arrays.toString(f).length() - 1),
                    Arrays.toString(l).substring(1, Arrays.toString(l).length() - 1),
                    Arrays.toString(r).substring(1, Arrays.toString(r).length() - 1)};
            csvWriter.writeRecord(csvContent);
            csvWriter.close();
            System.out.println("--------write CSV successfully--------");
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("writeCSV: " + (System.currentTimeMillis() - a) + "ms\n");
    }

    private float[][] readCSV(String path, String name, String id) {
        long a = System.currentTimeMillis();
        float[][] features = new float[3][512];
        try {
            if (!new File(path).exists()) {
                new File(path).createNewFile();
            }
            // 创建CSV读对象 例如:CsvReader(文件路径，分隔符，编码格式);
            CsvReader reader = new CsvReader(path, ',', Charset.forName("UTF-8"));
            // 跳过表头 如果需要表头的话，这句可以忽略
            reader.readHeaders();
            // 逐行读入除表头的数据
            while (reader.readRecord()) {
                if (id.equals(reader.getValues()[0]) && name.equals(reader.getValues()[1])) {
                    System.out.println(name + " " + id);
                    features[0] = stringToFloatArr(reader.getValues()[2]);
                    features[1] = stringToFloatArr(reader.getValues()[3]);
                    features[2] = stringToFloatArr(reader.getValues()[4]);
                    loginStatus = 1;
                    break;
                }
            }
            reader.close();
            if (loginStatus == 0) {
                System.out.println("readCSV: " + (System.currentTimeMillis() - a) + "ms");
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("readCSV: " + (System.currentTimeMillis() - a) + "ms");
        return features;
    }

    private void checkCSV(String path, String name, String id) {
        long a = System.currentTimeMillis();
        /*状态  4:没有此人  5:查到此人  6:id重复*/
        regStatus = 4;
        try {
            // 创建CSV读对象 CsvReader(文件路径，分隔符，编码格式);
            File csv = new File(path);
            if (!csv.exists()) {
                csv.createNewFile();
                System.out.println("checkCSV : " + (System.currentTimeMillis() - a) + "ms\n");
                return;
            }
            CsvReader reader = new CsvReader(path, ',', Charset.forName("UTF-8"));
            // 跳过表头
            reader.readHeaders();
            // 逐行读入除表头的数据
            while (reader.readRecord()) {
                if (id.equals(reader.getValues()[0]) && name.equals(reader.getValues()[1])) {
                    regStatus = 5;
                } else if (id.equals(reader.getValues()[0])) {
                    regStatus = 6;
                }
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("checkCSV : " + (System.currentTimeMillis() - a) + "ms\n");
    }

    private float[] stringToFloatArr(String string) {
        float[] floatss = new float[512];
        int j = 0;
        StringBuffer floatString = new StringBuffer();
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            if (c != ',') {
                floatString.append(c);
            } else {
                floatss[j++] = Float.parseFloat(String.valueOf(floatString));
                i++;
                floatString = new StringBuffer();
            }
        }
        floatss[511] = Float.parseFloat(String.valueOf(floatString));
        return floatss;
//        long start = System.nanoTime();
//        String[] s = string.split(", ");
//        float[] floats = new float[s.length];
//        for (int i = 0; i < s.length; i++) {
//            floats[i] = Float.parseFloat(s[i]);
//        }
////        System.out.println("stringToFloatArr : " + (System.nanoTime() - start) + "ns");
//        System.out.println(Arrays.toString(floats));
//        System.out.println();
//        System.out.println(Arrays.toString(floatss));
//        return floats;
    }

    public float[][][][] getImagePixel(String image) throws Exception {
        /**
         * @description: 得到图片的rgb值
         * @param image: 图片路径
         * @return: float[][][][] [batch][height][width][channel] batch为输入模型时必须值
         * @author: JiaZe
         * @date: 2020/5/11
         */
        float[][][][] output = new float[1][160][160][3];
        int length = 160 * 160 * 3;
        int[] rgb = new int[3];
        File file = new File(image);
        BufferedImage bi = null;
        try {
            bi = ImageIO.read(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
        /*规定图片尺寸为160*160 */
        int width = 160;
        int height = 160;
        int minx = 0;
        int miny = 0;
        float total = 0;
        for (int i = miny; i < width; i++) {
            for (int j = minx; j < height; j++) {
                // 下面三行代码将一个数字转换为RGB数字
                int pixel = bi.getRGB(j, i);
                rgb[0] = (pixel & 0xff0000) >> 16;
                rgb[1] = (pixel & 0xff00) >> 8;
                rgb[2] = (pixel & 0xff);
                output[0][i][j][0] = (float) ((pixel & 0xff0000) >> 16);
                output[0][i][j][1] = (float) ((pixel & 0xff00) >> 8);
                output[0][i][j][2] = (float) ((pixel & 0xff));
                total += (output[0][i][j][0] + output[0][i][j][1] + output[0][i][j][2]);
            }
        }
        /*平均值*/
        double mean = total / length;

        double std = 0;
        for (int i = miny; i < width; i++) {
            for (int j = minx; j < height; j++) {
                for (int k = 0; k < 3; k++) {
                    std += Math.pow(output[0][i][j][k] - mean, 2);
                }
            }
        }
        /*标准差*/
        std = std / length;
        std = Math.sqrt((double) std);
        double std_adj = Math.max(std, 1 / Math.sqrt(length));

        for (int i = miny; i < width; i++) {
            for (int j = minx; j < height; j++) {
                for (int k = 0; k < 3; k++) {
                    /*图片数据归一化*/
                    output[0][i][j][k] = (float) ((output[0][i][j][k] - mean) / std_adj);
                }
            }
        }
        return output;
    }

    private void generateImage(String[] imgArr, String basePath) throws Exception {// 对字节数组字符串进行Base64解码并生成图片
        /**
         * @description:
         * @param imgArr:
         * @return: void
         * @author: JiaZe
         * @date: 2020/5/11
         */
        FileOutputStream out;
        String folderPath = basePath + "/" + imgArr[0];
        File file = new File(folderPath);
        if (!file.exists()) {
            file.mkdir();
        }
//        System.out.println(folderPath);
        try {
            for (int i = 1; i < imgArr.length; i++) {
                String picName = folderPath + "/" + imgArr[0] + "_" + i + ".jpg";
                out = new FileOutputStream(picName);
                byte[] imgData = Base64.getDecoder().decode(imgArr[i]);
                out.write(imgData);
                out.flush();
                out.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*前端传递egb时的代码*/
    /*
    private float[] calAvgFeature(String[] imageString, int number) {
        long a = System.currentTimeMillis();
        float[] avg = new float[512];
        float[][][][] imageFloat = new float[number][160][160][3];
        float[][][] image;
        for (int i = 0; i < number; i++) {
            long b = System.currentTimeMillis();
            for (int j = 0; j < 160; j++) {
                for (int k = 0; k < 160; k++) {
                    imageFloat[i][j][k][0] = Float.parseFloat(imageString[i * 76800 + j * k * 3]);
                    imageFloat[i][j][k][1] = Float.parseFloat(imageString[i * 76800 + j * k * 3 + 1]);
                    imageFloat[i][j][k][2] = Float.parseFloat(imageString[i * 76800 + j * k * 3 + 2]);
                }
            }
            System.out.println("parsefloat: " + (System.currentTimeMillis() - b) + "ms\n");
        }

        for (int i = 0; i < number; i++) {
            image = imageFloat[i];
            float[] tmp = calFeature(image);
            for (int x = 0; x < 512; x++) {
                avg[x] += tmp[x];
                if (i == number - 1) {
                    avg[x] = avg[x] / number;
                }
            }
        }
        System.out.println("calAvgFeature: " + (System.currentTimeMillis() - a) + "ms\n");
        return avg;
    }

    private float[] calFeature(float[][][] image) {
        float[][][][] test=preWhiten(image);
        System.out.println(Arrays.toString(test[0][3][3]));
        Tensor inputX = Tensor.create(test);
        Tensor inputY = Tensor.create(false);

        long a = System.currentTimeMillis();
        Tensor result = tfSession.runner().feed("image_batch", inputX).feed("phase_train", inputY).fetch("embeddings").run().get(0);
        System.out.println("runner: " + (System.currentTimeMillis() - a) + "ms\n");
        float[][] ans = new float[1][512];
        result.copyTo(ans);
        return ans[0];
    }
    */

//    private float[][] loadModel(String path) {
//        /**
//         * @description: 加载模型
//         * @param :
//         * @return: void
//         * @author: JiaZe
//         * @date: 2020/5/11
//         */
//        float[][] features = new float[3][512];
//        try {
////            SavedModelBundle model = SavedModelBundle.load("D:/PDF/java/SimpleServlet/web/method1/123", "TAG");
////            Session tfSession = model.session();
//            features[0] = calAvgFeature(path + "/front");
//            features[1] = calAvgFeature(path + "/left");
//            features[2] = calAvgFeature(path + "/right");
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return features;
//    }


}