package org.twak.viewTrace.franken;

import org.twak.utils.collections.MultiMap;
import org.twak.viewTrace.facades.FRect;
import org.twak.viewTrace.facades.HasApp;

public class Tex2Panes extends App {

	public Tex2Panes(HasApp ha) {
		super(ha, "facade", 8, 256);
	}
	
	@Override
	public App getUp() {
		return ((FRect)hasA).mf.app;
	}

	@Override
	public MultiMap<String, App> getDown() {
		return null;
	}

	@Override
	public App copy() {
		return null;
	}

	@Override
	public void computeSelf( Runnable runnable ) {
	}

}
