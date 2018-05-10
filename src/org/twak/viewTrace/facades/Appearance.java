package org.twak.viewTrace.facades;

import static org.twak.viewTrace.facades.Appearance.NetConfig.Facade2Labels;
import static org.twak.viewTrace.facades.Appearance.NetConfig.Roof;
import static org.twak.viewTrace.facades.Appearance.NetConfig.Windows;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.Collections;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.twak.tweed.gen.SuperFace;
import org.twak.tweed.gen.skel.MiniRoof;
import org.twak.utils.geom.DRectangle;
import org.twak.utils.ui.AutoEnumCombo;
import org.twak.utils.ui.AutoEnumCombo.ValueSet;
import org.twak.utils.ui.ColourPicker;
import org.twak.utils.ui.FileDrop;
import org.twak.utils.ui.ListDownLayout;

public class Appearance {
	
	public enum TextureUVs {
		SQUARE, ZERO_ONE;
	}
	
	public enum AppMode {
		Color, Bitmap, Parent, Net
	}
	
	public AppMode appMode = AppMode.Color;
	public TextureUVs textureUVs = TextureUVs.SQUARE;
	public Color color = Color.gray;
	public String texture;
	public double[] styleZ;
	HasApp app;
	private NetConfig config;
	
	public enum NetConfig {
		
		Facade2Labels (8, 256, "facade2"),
		Roof (8, 512, "roof2"),
		Windows (8, 256, "dows1");
		
		String netName;
		int sizeZ;
		int resolution;
		
		NetConfig (int sizeZ, int resolution, String netName, NetConfig...children ) {
			this.sizeZ = sizeZ;
			this.netName = netName;
			this.resolution = resolution;
		}
	}
	
	public Appearance( Appearance a ) {
		this.appMode = a.appMode;
		this.textureUVs = a.textureUVs;
		this.color = a.color;
		this.texture = a.texture;
		this.styleZ = a.styleZ;
	}
	
	public Appearance(HasApp ha) {
		
		this.app = ha;
		
		if ( app.getClass() == MiniFacade.class) {
			this.config = Facade2Labels;
		} else if (app.getClass() == MiniRoof.class) {
			this.config = Roof;
		} else if (app.getClass() == FRect.class) {
			this.config = Windows;
		}		
		
		this.styleZ = new double[this.config.sizeZ];
	}

	public void update (Runnable update) {
		
		if (app instanceof MiniFacade)
			new Pix2Pix().facade( Collections.singletonList( (MiniFacade) app ), config, styleZ, update );
		else if (app instanceof SuperFace) 
			new Pix2Pix().roofs( ((MiniRoof)app), config, styleZ, update );
		else if (app instanceof FRect) 
			new Pix2Pix();
	}
	
	public JComponent createUI( HasApp ha, Runnable globalUpdate ) {

		JPanel out = new JPanel(new BorderLayout() );
		
		JPanel options = new JPanel();
		
		AutoEnumCombo combo = new AutoEnumCombo( appMode, new ValueSet() {
			public void valueSet( Enum num ) {
				appMode = (AppMode) num;
				options.removeAll();
				
				options.setLayout( new ListDownLayout() );
				buildLayout(appMode, options, new Runnable() {
					
					@Override
					public void run() {
						Appearance.this.update( globalUpdate );
					}
				} );
				options.repaint();
				options.revalidate();
			}
		} );
		
		out.add( combo, BorderLayout.NORTH );
		out.add( options, BorderLayout.CENTER );
		
		
//		double[] z;
//		
//		FeatureGenerator gf = (FeatureGenerator) se.toEdit.featureGen;
//		if ( gf.facadeStyle != null )
//			z = gf.facadeStyle;
//		else
//			z = gf.facadeStyle = new double[Pix2Pix.LATENT_SIZE];
		
//		List<MiniFacade> sameStyle = new ArrayList();
//		for (HalfEdge e : sf ){
//			SuperEdge se2 = (SuperEdge)e;
//			if ( se2.toEdit.featureGen.facadeStyle == z)
//				sameStyle.add( se2.toEdit );
//		}

		
		return out;
	}

	private void buildLayout( AppMode appMode, JPanel out, Runnable r ) {
		switch (appMode) {
		case Color:
			JButton col = new JButton("color");
			col.addActionListener( e -> new ColourPicker(null, color) {
				@Override
				public void picked( Color color ) {
					Appearance.this.color = color;
					r.run();
				}
			} );
			out.add( col );
			break;
		case Bitmap:
		default:
			out.add( new JLabel("no options") );
			break;
		case Net:

			NSliders sliders = new NSliders( styleZ, r );
			FileDrop drop = new FileDrop( "style" ) {
				public void process(java.io.File f) {
					new Pix2Pix().encode( f, config, styleZ, new Runnable() {
						@Override
						public void run() {
							sliders.setValues( styleZ );
							r.run();
						}
					} );
				};
			};
			
			out.add( sliders );
			out.add( drop );
			
			break;
		}
	}
}
