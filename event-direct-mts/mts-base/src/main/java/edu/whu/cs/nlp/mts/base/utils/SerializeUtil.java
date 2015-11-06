package edu.whu.cs.nlp.mts.base.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * 序列化工具类
 *
 * @author ZhenchaoWang 2015-11-3 16:12:11
 *
 */
public class SerializeUtil {

    /**
     * 反序列化对象
     *
     * @param filename
     * @return
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public static Object readObj(String filename) throws IOException, ClassNotFoundException {

        Object obj = null;

        ObjectInputStream inner = null;
        try {
            inner = new ObjectInputStream(new FileInputStream(filename));
            obj = inner.readObject();
        } finally {
            if(inner != null) {
                inner.close();
            }
        }

        return obj;

    }

    /**
     * 序列化对象
     *
     * @param obj
     * @param filename
     * @throws IOException
     */
    public static void writeObj(Object obj, File file) throws IOException {

        ObjectOutputStream outer = null;
        try {
            if(!file.getParentFile().exists()) {
                // 如果目录不存在，则创建
                file.getParentFile().mkdirs();
            }
            outer = new ObjectOutputStream(new FileOutputStream(file));
            outer.writeObject(obj);
            outer.flush();
        } finally {
            if(outer != null) {
                outer.close();
            }
        }

    }

}
