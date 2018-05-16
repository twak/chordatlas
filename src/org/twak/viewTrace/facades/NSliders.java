package org.twak.viewTrace.facades;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.Random;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.twak.utils.ui.ListDownLayout;

public class NSliders extends JPanel {

	double[] results;
	JSlider[] sliders;
	boolean updating = false;
	Runnable c;
	
	public NSliders (double[] result, Runnable update) {
		
		this.results = result;
		this.c = update;
		
		sliders = new JSlider[result.length];

		setLayout( new ListDownLayout() );
		
		for (int i = 0; i < result.length; i++) {
			
			int i_ = i;
			JSlider s = new JSlider( 0, 1000, 500 );
			s.setPreferredSize( new Dimension (100, s.getPreferredSize().height) );
			s.setValue( (int)( result[i] * 250 + 500) );
//			s.setOrientation( SwingConstants.HORIZONTAL );
			sliders[i] = s;
			add( s );
			
			s.addChangeListener( new ChangeListener() {
				@Override
				public void stateChanged( ChangeEvent e ) {
					change( (JSlider) e.getSource(), i_);
				}
			} );
		}
		
		JButton zero = new JButton("zero");
		zero.addActionListener( new ActionListener() {
			
			@Override
			public void actionPerformed( ActionEvent e ) {
				for (int i = 0; i < result.length; i++)
					result[i] = 0;
				
				setValues(result);
			}
		} );
		add (zero);
		
		JButton rand = new JButton("random");
		rand.addActionListener(  new ActionListener() {
			Random randy = new Random();
			@Override
			public void actionPerformed( ActionEvent e ) {
				
				for (int i = 0; i < result.length; i++)
					result[i] = randy.nextGaussian();
				
				setValues(result);
			}
		}  );
		add (rand);
	}

	public void setValues (double[] newValues) {
		updating = true;
		for (int i = 0; i < newValues.length; i ++) {
			System.out.println( newValues[i] );
			sliders[i].setValue( (int) ( (newValues[i] + 2) * 250 ) );
		}
		updating = false;
		new Thread (() -> c.run()).start();
	}
	
	private void change(JSlider s, int i) {
		results[i] = s.getValue() / 250.  - 2;
		if (!updating && !s.getValueIsAdjusting()) {
			new Thread (() -> c.run()).start();
		}
	}
	
}
