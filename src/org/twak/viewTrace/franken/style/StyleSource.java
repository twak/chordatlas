package org.twak.viewTrace.franken.style;

import java.util.Random;

import javax.swing.JPanel;

import org.twak.utils.ui.AutoEnumCombo.ValueSet;
import org.twak.viewTrace.franken.App;
import org.twak.viewTrace.franken.SelectedApps;

public interface StyleSource {
	
	public double[] draw(Random random, App app);
	public JPanel getUI(Runnable update, SelectedApps sa);
	public boolean install( SelectedApps selectedApps ); // return true to handle installation yourself
	public StyleSource copy();
}
