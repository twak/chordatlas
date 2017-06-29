package org.twak.mmg.media;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.twak.mmg.MOgram;
import org.twak.utils.ui.AutoDoubleSlider;
import org.twak.utils.ui.ColourPicker;

public class DepthColor extends RenderData {
	
	boolean visible;
	Color color;
	double depth;
	
    @Override
    public JComponent getUI(MOgram mogram) {
    	
    	JCheckBox visCheck = new JCheckBox("render to sketch");
    	visCheck.setSelected(visible);
    	visCheck.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
            	 visible = visCheck.isSelected();
                 mogram.somethingChanged();
            }
    	});
    	
		visCheck.setPreferredSize(new Dimension( (int) visCheck.getPreferredSize().getWidth(), 30));
		JPanel out = new JPanel(new FlowLayout());
		out.add(visCheck);
		
		JButton c = new JButton("color");
		c.addActionListener( k -> new ColourPicker(color) {
			
			@Override
			public void picked( Color color ) {
				DepthColor.this.color = color;
				mogram.somethingChanged();
				
			}
		} );
		out.add( c );
		
		out.add( new AutoDoubleSlider( this, "depth", "depth", -1, 1, () -> mogram.somethingChanged() ) );
		
    	return out;
    }
	
}
