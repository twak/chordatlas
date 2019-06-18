package org.twak.viewTrace.franken;

public abstract class GlobalUpdate implements Runnable {
	
	
	@Override
	public void run() {
		Thread.dumpStack();
		System.err.println( "implement me" );
	}
	
	public void run (Class<? extends App> justFinished) {
		run();
	}

}
