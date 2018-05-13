package org.twak.viewTrace.franken;

import org.twak.utils.collections.MultiMap;
import org.twak.viewTrace.facades.HasApp;

public class Tex2Panes extends App {

	public Tex2Panes(HasApp ha) {
		super(ha, "facade", 8, 256);
	}
	
	@Override
	public App getUp() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public MultiMap<String, App> getDown() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public App copy() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void computeSelf( Runnable runnable ) {
		// TODO Auto-generated method stub
		
	}

}
