package ca.baosiek.utils;

import java.io.*;
import java.util.concurrent.*;

public class ShellExecutor {

    private static String directory;

    public static String exec(String command){

        ProcessBuilder builder = new ProcessBuilder();
        Future<String> r = null;
        try {
            if (directory == null){
                directory = System.getProperty("user.home");
                System.out.println(directory + " is null");
            }
            builder.directory(new File(directory));
            builder.command("sh", "-c", command);
            Process process = builder.start();

            StreamGobbler streamGobbler =
                    new StreamGobbler(process.getInputStream());
            ExecutorService executor = Executors.newSingleThreadExecutor();
            r = executor.submit(streamGobbler);
            int exitCode = process.waitFor();
            executor.shutdown();
            return r.get();

        } catch (IOException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        return "error";
    }

    public static void setDirectory(String dir){
        directory = dir;
    }

    private static class StreamGobbler implements Callable<String> {

        private InputStream inputStream;
        private StringBuffer sb;

        public StreamGobbler(InputStream inputStream){
            this.inputStream = inputStream;
            this.sb = new StringBuffer();
        }
        @Override
        public String call() {
            new BufferedReader(new InputStreamReader(inputStream)).lines()
                    .forEach(l -> this.sb.append(l+"\n"));
            return sb.toString();
        }
    }
}
