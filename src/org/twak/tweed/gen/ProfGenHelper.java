package org.twak.tweed.gen;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.twak.tweed.Tweed;
import org.twak.utils.ui.Plot;

import com.thoughtworks.xstream.XStream;

public class ProfGenHelper {

	public ProfGenHelper( List<Prof> profs ) {
		
		
		JSlider slider = new JSlider(  SwingConstants.VERTICAL, 0, profs.size()-1, 0);
		
		JButton button = new JButton("dump");
		JButton again = new JButton("again again");
		
		final Plot pot = new Plot(slider, button, again, profs.get(0) );

		ChangeListener cl = new ChangeListener() {
			
			@Override
			public void stateChanged( ChangeEvent arg0 ) {
				
				System.out.println( "at "+slider.getValue() );
				
				pot.toPaint.clear();
				pot.toPaint.add( profs.get( slider.getValue() ) );
				pot.toPaint.add( profs.get( slider.getValue() ).parameterize() );
				
				pot.repaint();
			}
		};
		
		again.addActionListener( e -> cl.stateChanged( null ) );
		
		button.addActionListener( new ActionListener() {
			
			@Override
			public void actionPerformed( ActionEvent e ) {
				BufferedImage bi = new BufferedImage (pot.getWidth(), pot.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
				
				for (File f : new File ( Tweed.SCRATCH +  "vid").listFiles())
					f.delete();
				
				int c = 0;
				
				for (int i = slider.getMinimum(); i < slider.getMaximum(); i+=1) {
					slider.setValue( i );
					pot.publicPaintComponent( bi.getGraphics() );
					try {
						ImageIO.write( bi, "png", new File ( String.format ( Tweed.SCRATCH +  "vid/%05d.png", c++ ) ));
						
					} catch ( IOException f ) {
						f.printStackTrace();
					}
				}
				
			}
		} );
		
		
		
		slider.addChangeListener( cl );
	}

	public static void main (String[] args) {
		
		new ProfGenHelper( (List<Prof>) new XStream().fromXML( new File( Tweed.SCRATCH + "profiles.xml" ) ) );
		
	}
}
