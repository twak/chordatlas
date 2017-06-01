package org.twak.footprints;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.function.Consumer;
import java.util.stream.Stream;

import redis.clients.jedis.Jedis;

public class HeightsToRedis {

	public final static String HEIGHT = "_absh2", ROOF = "_abshmax";
	
	private static Jedis jedis =  new Jedis("localhost");
	
	public static double getHeight(String name) {
		return Double.parseDouble( jedis.get(name + HEIGHT) );
	}
	
	public static double getRoof(String name) {
		return Double.parseDouble( jedis.get(name + ROOF) );
	}
	
	public static void loadHeightsToRedis() {
		
		final int[] count = new int[1];
		
		for (File csv : new File("/home/twak/data/Download_around_ucl_562795").listFiles() ) {
			if (csv.getName().endsWith(".csv")) {
				System.out.println("loading to redis "+ csv.getName());
				
				
				try (Stream<String> stream = Files.lines(csv.toPath())) {

					stream.forEach(new Consumer<String>() {
						@Override
						public void accept(String line) {
							String[] vals = line.split(",");
							String name = vals[1];
							jedis.set(name+HEIGHT, vals[6]);
							jedis.set(name+ROOF  , vals[7]);
							count[0]++;
						}
					});

				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} 
				
			}
		}
		System.out.println(count[0] + " records loaded");
	}

//	private Jedis getJedis() {
//		if (jedis == null) {
//			jedis = new Jedis("localhost:6379");
//			if (!jedis.isConnected()) {
//				System.err.println("couldn't connect to reddis");
//				System.exit(0);
//				try {
//					Process p = Runtime.getRuntime().exec("/home/twak/code/redis-3.2.3/src/redis-server &");
//					while (true) {
//						p.getErrorStream();
//					}
//					p.waitFor();
//				} catch (Throwable e) {
//					e.printStackTrace();
//					return jedis = null;
//				}
//				jedis = new Jedis("localhost");
//			}
//		}
//		return jedis;
//	}

	public static void main(String[] args) {
		HeightsToRedis.loadHeightsToRedis();
	}
}
