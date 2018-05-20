package org.twak.viewTrace.franken.style;

import java.util.Random;

import javax.swing.JPanel;

import org.twak.viewTrace.franken.App;

public interface StyleSource {
	
	public double[] draw(Random random, App app);
	public JPanel getUI(Runnable update);
	
}
