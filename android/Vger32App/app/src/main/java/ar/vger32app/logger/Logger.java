package ar.vger32app.logger;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Deque;
import java.util.LinkedList;

/*
 * Abstract file-backed logger. Keeps up to maxLines entries in memory,
 * batches writes to disk every WRITE_EVERY_N entries, and flushes on demand.
 */

public abstract class Logger {

    private static final int WRITE_EVERY_N = 10;

    private int maxLines = 1000;
    private int pendingWrites = 0;

    protected final Context context;
    private final Deque<String> logLines = new LinkedList<>();
    private String fileName;

    public Logger(Context context) {
        this.context = context.getApplicationContext();
    }

    public Logger(Context context, String fileName) {
        this.context = context.getApplicationContext();
        this.fileName = fileName + ".log";
    }

    public Logger(Context context, String fileName, int maxLines) {
        this.context = context.getApplicationContext();
        this.fileName = fileName + ".log";
        this.maxLines = maxLines;
    }

    // --------------------------------------------------------
    // --- PUBLIC API -----------------------------------------

    protected synchronized void addLineEntry(String line) {
        ensureLoaded();
        logLines.addFirst(line);
        while (logLines.size() > maxLines) logLines.removeLast();
        if (++pendingWrites >= WRITE_EVERY_N) {
            saveLogLines();
            pendingWrites = 0;
        }
    }

    public synchronized String getLog() {
        ensureLoaded();
        StringBuilder sb = new StringBuilder();
        for (String line : logLines) sb.append(line).append("\n");
        return sb.toString();
    }

    public synchronized void flush() {
        if (pendingWrites > 0) {
            saveLogLines();
            pendingWrites = 0;
        }
    }

    public synchronized void clean() {
        logLines.clear();
        pendingWrites = 0;
        try (FileOutputStream fos = context.openFileOutput(fileName, Context.MODE_PRIVATE)) {
            fos.write("".getBytes());
        } catch (IOException e) {
            Log.e("Logger", "clean failed: " + e.getMessage());
        }
    }

    public synchronized int size() {
        ensureLoaded();
        return logLines.size();
    }

    // --------------------------------------------------------
    // --- PRIVATE --------------------------------------------

    private void ensureLoaded() {
        if (logLines.isEmpty() && fileName != null) {
            loadLogLines();
        }
    }

    private void loadLogLines() {
        File file = new File(context.getFilesDir(), fileName);
        if (!file.exists()) return;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file)))) {
            String line;
            while ((line = br.readLine()) != null) {
                logLines.addLast(line);
            }
        } catch (IOException e) {
            Log.e("Logger", "load failed: " + e.getMessage());
        }
    }

    private void saveLogLines() {
        File file = new File(context.getFilesDir(), fileName);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file, false))) {
            for (String line : logLines) {
                bw.write(line);
                bw.newLine();
            }
        } catch (IOException e) {
            Log.e("Logger", "save failed: " + e.getMessage());
        }
    }
}