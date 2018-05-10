package org.twak.viewTrace.facades;

import java.awt.Color;
import java.util.List;

import javax.swing.JComponent;

import org.twak.tweed.gen.SuperFace;
import org.twak.utils.ui.FileDrop;
import static org.twak.viewTrace.facades.Appearance.NetConfig.*;

public class Appearance {
	
	public enum TextureUVs {
		SQUARE, ZERO_ONE;
	}
	
	public enum AppMode {
		Color, Texture, Parent, Net
	}
	
	public AppMode appMode = AppMode.Color;
	public TextureUVs textureUVs = TextureUVs.SQUARE;
	public Color color = Color.gray;
	public String texture;
	public double[] styleZ;
	HasApp app;
	private NetConfig config;
	
	public enum NetConfig {
		
		Facade2Labels (8, "facade2"),
		Roof (8, "roof2"),
		Windows (8, "dows1");
		
		String netName;
		int sizeZ;
		
		NetConfig (int sizeZ, String netName, NetConfig...children ) {
			this.sizeZ = sizeZ;
			this.netName = netName;
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
		} else if (app.getClass() == SuperFace.class) {
			this.config = Roof;
		} else if (app.getClass() == FRect.class) {
			this.config = Windows;
		}		
	}

	public void update (Runnable update) {
		
	}
	
	public JComponent createUI( HasApp ha, Runnable update ) {

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
		
		NSliders sliders = new NSliders(z, c);
		
		FileDrop drop = new FileDrop( "style" ) {
			public void process(java.io.File f) {
				new Pix2Pix().encode( f, z, new Runnable() {
					@Override
					public void run() {
						sliders.setValues( z );
					}
				} );
			};
		};
	}
}
