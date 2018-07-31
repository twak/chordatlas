package org.twak.tweed;

import java.awt.Component;
import java.awt.Font;

import javax.swing.JLabel;

public class Fontz {

	public static JLabel setBold( JLabel tl ) {
		tl.setFont(tl.getFont().deriveFont(tl.getFont().getStyle() | Font.BOLD));

		return tl;
	}
	
	public static JLabel setItalic( JLabel tl ) {
		tl.setFont(tl.getFont().deriveFont(tl.getFont().getStyle() | Font.ITALIC));
		
		return tl;
	}

}
