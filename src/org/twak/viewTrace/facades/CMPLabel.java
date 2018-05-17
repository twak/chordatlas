package org.twak.viewTrace.facades;

import java.awt.Color;

public enum CMPLabel { //sequence is  z-order
		Background (1, 0,0, 170 ),
		Facade     (2, 0,0, 255 ),
		Molding    (10, 255, 85, 0 ),
		Cornice    (5, 0, 255, 255 ),
		Pillar     (11, 255,0, 0 ),
		Window     (3, 0, 255, 0 ),
//		Window     (3, 0, 85, 255 ),
		Door       (4, 0,170, 255 ),
		Sill       (6, 85,255, 170 ),
		Blind      (8, 255,255, 0 ),
		Balcony    (7, 170,255, 85 ),
		Shop       (12, 170,0, 0 ),
		Deco       (9, 255,170, 0 );
		
		final int index;
		public final Color rgb;
		
		CMPLabel (int index, int r, int g, int b) {
			this.rgb = new Color (r,g,b);
			this.index = index;
		}
	}