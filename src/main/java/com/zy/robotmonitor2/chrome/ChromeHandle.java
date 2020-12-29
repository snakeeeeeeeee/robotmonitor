package com.zy.robotmonitor2.chrome;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import org.apache.commons.io.FileUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.util.ResourceUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;

/**
 * @author zy 2020/12/28
 */
public class ChromeHandle implements Job {


    private static int retry = 0;

    private static String filePath;
    private static String clientImgPath;
    private static String videoRingImgPath;

    static {
        try {
            File path = new File(ResourceUtils.getURL("classpath:").getPath());
            if (!path.exists()) {
                path = new File("");
            }

            File upload = new File(path.getAbsolutePath(), "images");
            if (!upload.exists()) {
                upload.mkdirs();
            }

            filePath = upload.getAbsolutePath() + File.separator;
            clientImgPath = filePath + "client.png";
            videoRingImgPath = filePath + "videoRing.png";

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void init() {
        try {
            Properties properties = initProperties();
            String startExecute = properties.getProperty("start_execute");
            if (Boolean.parseBoolean(startExecute)) {
                doExecute();
            }
            //启动定时任务
            schedulerStart(properties);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void execute(JobExecutionContext jobExecutionContext) {
        doExecute();
    }

    private static void schedulerStart(Properties properties) throws SchedulerException {
        JobDetail jobDetail = JobBuilder.newJob(ChromeHandle.class)
                .withDescription("自动化监控定时任务")
                .withIdentity("robot_monitor", "robot_monitor")
                .build();

        //创建一个trigger触发规则
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("robot_monitor", "robot_monitor")
                .withSchedule(CronScheduleBuilder.cronSchedule(properties.getProperty("cron"))) //使用cron表达式来执行定时任务
                .build();

        //创建一个调度器，也就是一个Quartz容器
        //声明一个scheduler的工厂schedulerFactory
        SchedulerFactory schedulerFactory = new StdSchedulerFactory();
        //通过schedulerFactory来实例化一个Scheduler
        Scheduler scheduler = schedulerFactory.getScheduler();
        //将Job和Trigger注册到scheduler容器中
        scheduler.scheduleJob(jobDetail, trigger);
        //启动
        scheduler.start();
    }


    public static void doExecute() {
        try {
            //初始化配置信息
            Properties properties = initProperties();

            getImagePath("client_url", "client.png", properties, null);
            getImagePath("video_ring_url", "videoRing.png", properties, null);
            sendMessageToWechat(clientImgPath, videoRingImgPath, properties.getProperty("wecahr_window_name"), properties);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void sendMessageToWechat(String clientImagePath, String videoRingImagePath, String weChatWindowName, Properties properties) throws Exception {
        WinDef.HWND hwnd = User32.INSTANCE.FindWindow("ChatWnd", weChatWindowName);
        if (hwnd == null) {
            System.out.println("WeChat is not running");
        } else {
            User32.INSTANCE.ShowWindow(hwnd, 9); // SW_RESTORE
            //函数将创建指定窗口的线程放入前台并激活该窗口。键盘输入指向窗口，并为用户更改各种视觉提示。
            User32.INSTANCE.SetForegroundWindow(hwnd);

            //生成一个模仿人操作行为的机器人
            Robot robot = new Robot();
            //粘贴图片到微信
            doPaste(robot, clientImagePath);
            doPaste(robot, videoRingImagePath);
            //粘贴文本内容
            pasteStrToWeChat(robot, properties.getProperty("msg_content"));
            //回车发送
            //robot.keyPress(KeyEvent.VK_ENTER);
        }
    }

    private static void pasteStrToWeChat(Robot robot, String text) throws Exception {
        String msgTimeStr = getMsgTimeStr();
        String msg = String.format(text, msgTimeStr);
        //将文本内容粘贴到剪切板
        setClipboardString(msg);
        //粘贴到微信
        robotPasteToWeChat(robot);
    }

    private static void doPaste(Robot robot, String imagePath) throws Exception {
        //拷贝图片到剪贴板
        copyImage(imagePath);
        //粘贴到微信
        robotPasteToWeChat(robot);
    }

    private static void robotPasteToWeChat(Robot robot) throws InterruptedException {
        // 按下Control键
        robot.keyPress(KeyEvent.VK_CONTROL);
        //模拟用户慢半秒按V
        Thread.sleep(500);
        // 按下V键
        robot.keyPress(KeyEvent.VK_V);
        // 释放ctrl按键
        robot.keyRelease(KeyEvent.VK_CONTROL);
    }

    private static void getImagePath(String openUrl, String imageName, Properties properties, ChromeDriver driver) throws Exception {
        try {
            //初始化谷歌浏览器驱动
            if (driver == null) {
                driver = getChromeDriver(properties);
            }
            driver.get(properties.getProperty(openUrl));

            //登录
            login(driver, properties);
            //保存截图信息
            saveImage(driver, imageName);
            driver.close();
        } catch (Exception e) {
            e.printStackTrace();
            if (retry < Integer.parseInt(properties.getProperty("open_fail_retry_max"))) {
                retry++;
                //重试间隔时间
                try {
                    Thread.sleep(Long.parseLong(properties.getProperty("open_fail_retry_interval")));
                } catch (Exception ex) {
                }
                System.out.println("打开监控地址出错。 正在进行进行第: " + retry + "次重试");
                getImagePath(openUrl, imageName, properties, driver);
            } else {
                System.out.println("打开监控地址出错。 重试次数已用尽");
            }
        }
        //重置重试次数
        retry = 0;
    }

    private static String getMsgTimeStr() {
        //当前时间
        LocalDateTime now = LocalDateTime.now();
        int nowMinute = now.getMinute();
        String nowStr;
        if (nowMinute >= 30) {
            nowStr = now.getHour() + ":30";
        } else {
            nowStr = now.getHour() + ":00";
        }

        //前半小时时间
        LocalDateTime front = LocalDateTime.now().plusMinutes(-30);
        int frontMinute = front.getMinute();
        String frontStr;
        if (frontMinute >= 30) {
            frontStr = front.getMonthValue() + "月" + front.getDayOfMonth() + "日" + front.getHour() + ":30";
        } else {
            frontStr = front.getMonthValue() + "月" + front.getDayOfMonth() + "日" + front.getHour() + ":00";
        }
        return frontStr + " - " + nowStr;
    }

    private static String saveImage(ChromeDriver driver, String imageName) throws IOException {
        //获取内容区域元素
        File srcFile = driver.getScreenshotAs(OutputType.FILE);
        //保存截图
        String path = filePath + imageName;
        System.out.println("srcFile: " + srcFile + ":::::: path: " + path);
        FileUtils.copyFile(srcFile, new File(path));
        return path;
    }

    private static void login(ChromeDriver driver, Properties properties) throws InterruptedException {
        //设置账号与密码， 这里因为两个css是一样的,且密码是一样的才能这样做，否则就要根据顺序去判断哪个是账号，哪个是密码了
        List<WebElement> elements = driver.findElements(By.className("css-1bjepp-input-input"));
        for (WebElement element : elements) {
            element.sendKeys(properties.getProperty("username"));
        }
        // 登录
        driver.findElement(By.className("css-6ntnx5-button")).sendKeys(Keys.ENTER);
        Thread.sleep(Long.parseLong(properties.getProperty("monitor_view_time")));
    }

    private static ChromeDriver getChromeDriver(Properties properties) {
        //绑定浏览器信息
        System.setProperty("webdriver.chrome.driver", properties.getProperty("webdriver.chrome.driver.path"));
        ChromeOptions options = new ChromeOptions();
        options.setBinary(properties.getProperty("chrome_exe.path"));
        ChromeDriver driver = new ChromeDriver(options);
        //浏览器全屏
        driver.manage().window().maximize();
        return driver;
    }


    private static Properties initProperties() throws Exception {
        Properties properties = new Properties();
        InputStream inputStream = ChromeHandle.class.getClassLoader().getResourceAsStream("config.properties");
        BufferedReader bf = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
        properties.load(bf);

        return properties;
    }

    /**
     * 复制图像到系统粘贴板  （实际上我们不用手绘Image对象直接用File对象得到）
     *
     * @param path 图片的地址
     */
    private static void copyImage(String path) {
        //将path得到的file转换成image
        Image image = null;
        File file = new File(path);
        BufferedImage bi;
        //通过io流操作把file对象转换成Image
        try {
            InputStream is = new FileInputStream(file);
            bi = ImageIO.read(is);
            image = bi;
        } catch (IOException e) {
            e.printStackTrace();
        }
        //复制到粘贴板上
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        MyImageSelection myImageSelection = new MyImageSelection(image);
        clipboard.setContents(myImageSelection, null);
    }

    /**
     * 将字符串赋值到系统粘贴板
     *
     * @param data 要复制的字符串
     */
    public static void setClipboardString(String data) {
        // 获取系统剪贴板
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        // 封装data内容
        Transferable ts = new StringSelection(data);
        // 把文本内容设置到系统剪贴板
        clipboard.setContents(ts, null);
    }


    public static class MyImageSelection implements Transferable {
        private Image image; //得到图片或者图片流

        public MyImageSelection(Image image) {
            this.image = image;
        }

        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            if (!DataFlavor.imageFlavor.equals(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return image;
        }

    }
}
