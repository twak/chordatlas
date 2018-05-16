package org.twak.viewTrace.franken.style;

import java.util.Random;

import javax.swing.JPanel;

public interface StyleSource {
	
	public double[] draw(Random random);
	public JPanel getUI(Runnable update);
	
}
