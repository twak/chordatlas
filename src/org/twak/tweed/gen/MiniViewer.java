package org.twak.tweed.gen;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JSlider;

import org.twak.tweed.gen.skel.AppStore;
import org.twak.utils.PaintThing;
import org.twak.utils.ui.Plot;
import org.twak.viewTrace.facades.MiniFacade;
import org.twak.viewTrace.facades.MiniFacadePainter;
import org.twak.viewTrace.facades.Regularizer;

public class MiniViewer {

	JSlider mini, animation;

	JCheckBox 
			  output = new JCheckBox( "output" ),
					  paintImage = new JCheckBox( "images", false );
	
	List<MiniFacade> toViz = new ArrayList<>();

	private SuperEdge se;

	AppStore ac;
	
	public MiniViewer( SuperEdge se, AppStore ac ) {


		this.ac = ac;
		this.se = se;
		toViz = se.toRegularize;
		
		mini = new JSlider( -1, 20, -1 );
		animation = new JSlider (-1, 100, 100);
		
		JButton go = new JButton("go");
		
		Plot plot = new Plot( new JLabel("image"), mini, new JLabel("regularize"), animation, output, paintImage, go );
		
		output.addActionListener( e -> plot( plot) );
		
//		edge.addChangeListener( ecl );
		mini.addChangeListener( e -> plot(  plot ) );
		animation.addChangeListener( e -> plot(  plot ) );
		go.addActionListener( e -> plot(  plot ) );
		
//		ecl.stateChanged( null );
		
		plot( plot);
	}

	private void plot(Plot plot ) {

		List<MiniFacade> minis;

		PaintThing.debug.clear();
		plot.toPaint.clear();
		
		MiniFacadePainter.PAINT_IMAGE = paintImage.isSelected();

		double[] range = se.findRange();
		
		if (range == null)
			range = new double[] {0, se.length() };
		
		minis = new Regularizer().debug( toViz, range[0], range[1], animation.getValue() / 100. );
		
		{
			int selected = mini.getValue();

//			if (selected != -1)
//				plot.toPaint.add ( ImageFeatures.fixAbsPath( minis.get( selected ).imageFeatures.ortho ).getAbsolutePath() );
			
			if (output.isSelected())
				plot.toPaint.add(minis.get( 0 ));
			else
			for ( int i = 0; i < minis.size(); i++ )
				if ( selected == -1 || selected == i )
					plot.toPaint.add( minis.get( i ) );
		}
		plot.repaint();
	}
}
