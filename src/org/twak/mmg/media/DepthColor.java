package org.twak.mmg.media;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.twak.mmg.MOgram;

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
    	return out;
    }
	
}
