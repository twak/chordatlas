package org.twak.viewTrace.franken.style.ui;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Random;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
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
	
	public NSliders (double[] result, Runnable update, Runnable extraButton ) {
		
		this.results = result;
		this.c = update;
		
		sliders = new JSlider[result.length];

		setLayout( new ListDownLayout() );
		
		add (new JLabel("μ:"));
		
		for (int i = 0; i < result.length; i++) {
			
			int i_ = i;
			JSlider s = new JSlider( 0, 1000, 500 );
			s.setPreferredSize( new Dimension (100, s.getPreferredSize().height / 2) );
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
		
		JButton zero = new JButton("μ=0");
		zero.addActionListener( new ActionListener() {
			
			@Override
			public void actionPerformed( ActionEvent e ) {
				for (int i = 0; i < result.length; i++)
					result[i] = 0;
				
				setValues(result);
			}
		} );
		
		JButton rand = new JButton("μ=rand");
		rand.addActionListener(  new ActionListener() {
			Random randy = new Random();
			@Override
			public void actionPerformed( ActionEvent e ) {
				
				for (int i = 0; i < result.length; i++)
					result[i] = randy.nextGaussian();
				
				setValues(result);
			}
		}  );
		
		JButton paste = new JButton("μ=paste");
		paste.addActionListener(  new ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {

				if ( UIVector.copiedVector != null && UIVector.copiedVector.length == result.length )
					for ( int i = 0; i < result.length; i++ )
						result[ i ] = UIVector.copiedVector[ i ];
				else {
					JOptionPane.showMessageDialog( null, "nothing found to paste / wrong length vector" );
					return;
				}
				
				setValues(result);
			}
		}  );
		
		JButton copy = new JButton("copy(μ)");
		copy.addActionListener(  e -> UIVector.copiedVector = result );
		
		JPanel meanOpts = new JPanel(new GridLayout( 2, 2 ) );
		meanOpts.add (zero);
		meanOpts.add (rand);
		meanOpts.add (paste);
		meanOpts.add (copy);
		
		add(meanOpts);
		
		if (extraButton != null) {
			JButton e = new JButton(extraButton.toString());
			e.addActionListener( f -> extraButton.run() );
			add(e);
		}
	}

	public void setValues (double[] newValues) {
		updating = true;
		for (int i = 0; i < newValues.length; i ++) {
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
