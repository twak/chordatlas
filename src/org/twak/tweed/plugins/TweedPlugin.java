package org.twak.tweed.plugins;

import java.util.List;

import org.twak.tweed.Tweed;
import org.twak.tweed.TweedFrame;
import org.twak.tweed.tools.Tool;
import org.twak.utils.ui.SimplePopup2;

public interface TweedPlugin {
	public void addToAddMenu(TweedFrame tf, SimplePopup2 sp );
	public void addTools(Tweed tweed, List<Tool> tools);
	public void addToNewScene( TweedFrame instance );
}
