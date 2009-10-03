package test;

import java.util.HashSet;
import java.util.Random;

public class Test {
    public static void main(String[] args) throws Exception {
        final StringBuffer sb = new StringBuffer();
        Runnable runnable = new Runnable() {
            public void run() {
                while (true) {
                    Random r = new Random(System.currentTimeMillis());
                    double d = r.nextDouble();
                    d += r.nextDouble();
                    d += Math.sin(d);
                    d += Math.cos(r.nextDouble());
                    sb.append(new String(d + ""));
                    boolean b = r.nextBoolean();
                    if(b) {
                        sb.setLength(0);
                    }
                }
            }
        };
        Thread t1 = new Thread(runnable);
        Thread t2 = new Thread(runnable);
        Thread t3 = new Thread(runnable);
//        t1.setPriority(Thread.MIN_PRIORITY);
//        t2.setPriority(Thread.MIN_PRIORITY);
        t1.start();
        t2.start();
        t3.start();

        HashSet<String> hashSet = new HashSet<String>();
        Class<? extends HashSet> clzz = hashSet.getClass();
        Class<? extends HashSet> clazz  = HashSet.class;

        if(clzz == null){
            //
        }
    }

    public <V> V getAdapter(Class<V> adapter) {
        return null;
    }

    public Object getAdapter2(Class adapter) {
        return null;
    }




}
