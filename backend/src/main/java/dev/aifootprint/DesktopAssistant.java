package dev.aifootprint;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;

/** Optional native launcher mode. CLI and test runs never install shortcuts or open a browser. */
@Component
public class DesktopAssistant {
    private static FileChannel channel;private static FileLock lock;
    private static boolean silent;
    private static String localUrl(){return "http://127.0.0.1:"+System.getenv().getOrDefault("AIF_PORT","17890")+"/assistant.html";}
    static boolean prepare(String[] args) throws Exception {
        if(!Boolean.getBoolean("aif.desktop")) return true;
        silent=Arrays.asList(args).contains("--aif-silent");
        String base=System.getenv().getOrDefault("LOCALAPPDATA",System.getProperty("user.home"));
        Path data=Path.of(System.getenv().getOrDefault("AIF_ASSISTANT_HOME",Path.of(base,"AI Footprint","data").toString())).toAbsolutePath();
        Files.createDirectories(data);
        channel=FileChannel.open(data.resolve("assistant.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        lock=channel.tryLock();
        if(lock==null){if(!silent) browse(localUrl());channel.close();return false;}
        System.setProperty("spring.datasource.url","jdbc:sqlite:"+data.resolve("footprint.db"));
        System.setProperty("logging.file.name",data.resolve("assistant.log").toString());
        System.setProperty("spring.main.headless","false");
        return true;
    }
    @EventListener(ApplicationReadyEvent.class)
    public void ready() {
        if(!Boolean.getBoolean("aif.desktop")) return;
        if(!silent && !Boolean.getBoolean("aif.no-browser")) browse(localUrl());
        if(Boolean.getBoolean("aif.no-tray") || !SystemTray.isSupported()) return;
        EventQueue.invokeLater(()->{
            try {
                BufferedImage image=new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
                g.setColor(new Color(40,96,70));g.fillRoundRect(0,0,32,32,10,10);g.setColor(Color.WHITE);g.setFont(new Font("SansSerif",Font.BOLD,21));g.drawString("A",8,24);g.dispose();
                PopupMenu menu=new PopupMenu();MenuItem open=new MenuItem("Open AI Footprint Assistant");open.addActionListener(e->browse(localUrl()));menu.add(open);
                MenuItem exit=new MenuItem("Quit Assistant");exit.addActionListener(e->System.exit(0));menu.add(exit);
                TrayIcon icon=new TrayIcon(image,"AI Footprint Assistant",menu);icon.setImageAutoSize(true);icon.addActionListener(e->browse(localUrl()));SystemTray.getSystemTray().add(icon);
            } catch(Exception ignored) { /* The local management page remains available without a tray. */ }
        });
    }
    private static void browse(String url) {
        try{if(Desktop.isDesktopSupported())Desktop.getDesktop().browse(URI.create(url));}catch(Exception ignored){}
    }
    static void startup(boolean enabled) throws Exception {
        String executable=System.getProperty("jpackage.app-path");
        if(!Boolean.getBoolean("aif.desktop") || executable==null || !System.getProperty("os.name").startsWith("Windows"))
            throw new IllegalStateException("开机启动仅供已安装的 Windows 采集助手使用");
        Path shortcut=Path.of(System.getenv("APPDATA"),"Microsoft","Windows","Start Menu","Programs","Startup","AI Footprint Assistant.lnk");
        if(!enabled){Files.deleteIfExists(shortcut);return;}
        String command="$s=(New-Object -ComObject WScript.Shell).CreateShortcut('"+shortcut.toString().replace("'","''")+"');$s.TargetPath='"+executable.replace("'","''")+"';$s.Arguments='--aif-silent';$s.Save()";
        String windowsRoot=System.getenv("SystemRoot");
        if(windowsRoot==null || windowsRoot.isBlank()) throw new IllegalStateException("无法定位 Windows 系统目录");
        Path powershell=Path.of(windowsRoot,"System32","WindowsPowerShell","v1.0","powershell.exe");
        if(!Files.isRegularFile(powershell)) throw new IllegalStateException("找不到 Windows 自带的 PowerShell");
        Process process=new ProcessBuilder(powershell.toString(),"-NoProfile","-NonInteractive","-WindowStyle","Hidden","-Command",command).start();
        if(!process.waitFor(10,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();throw new IllegalStateException("开机启动设置超时");}
        if(process.exitValue()!=0) throw new IllegalStateException("无法保存开机启动设置");
    }
}
