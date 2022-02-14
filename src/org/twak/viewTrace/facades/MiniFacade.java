package org.twak.viewTrace.facades;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.vecmath.Point2d;

import org.twak.tweed.TweedSettings;
import org.twak.tweed.gen.FeatureCache.ImageFeatures;
import org.twak.tweed.gen.SuperFace;
import org.twak.utils.PaintThing;
import org.twak.utils.geom.DRectangle;
import org.twak.viewTrace.franken.FacadeGreebleApp;
import org.twak.viewTrace.franken.FacadeLabelApp;
import org.twak.viewTrace.franken.FacadeSuperApp;
import org.twak.viewTrace.franken.FacadeTexApp;
import org.twak.viewTrace.franken.HasSuper;
import org.twak.viewTrace.franken.SuperSuper;

public class MiniFacade implements HasSuper, Serializable {
	
	public double width, height, groundFloorHeight, left;
	public boolean softLeft, softRight;
//	public double[] 
//			color = Arrayz.toDoubleArray( GreebleSkel.BLANK_WALL );
//			groundColor = null;
	
	public enum Feature {
		
		BALCONY (new Color (170,0,255), new Color (170,255,85) ),
		CORNICE (Color.blue, new Color (0,255, 255)), 
//		DOOR (new Color (170,255,0)), WINDOW ( new Color (0,255,170) ), // BigSUR paper colors
		DOOR (Color.blue, new Color (0,170, 255)),
		WINDOW ( Color.green, new Color ( 0, 85, 255 ) ), // classification colors
		SHOP (new Color (255,0,170), new Color (170,0,0)),
		SILL (new Color (255,170,0), new Color (85, 255, 170)),
		MOULDING ( new Color (0,255,170), new Color (255, 85, 0) ),
		GRID (Color.black, Color.black);
		
		public Color color, cmpCol;
		
		private Feature (Color c, Color cmp) {
			this.color = c;
			this.cmpCol = cmp;
		}
	}
	
	public FeatureGenerator featureGen = new FeatureGenerator(this);
	
	public List<Outer> outers = new ArrayList<>();
	public ImageFeatures imageFeatures;
	
	public List<Double> 
			hMargin = new ArrayList<>(), 
			vMargin = new ArrayList<>();
	
	double imageXM, scale;

	public Color wallColor;

	public SuperFace sf;

	WinGrid grid;
	
	public FacadeLabelApp facadeLabelApp = new FacadeLabelApp( this );
	public FacadeTexApp facadeTexApp = new FacadeTexApp( this );
	public FacadeSuperApp facadeSuperApp = new FacadeSuperApp( this );
	public FacadeGreebleApp facadeGreebleApp = new FacadeGreebleApp( this );
	
	static {
		PaintThing.lookup.put( MiniFacade.class, new MiniFacadePainter() );
		PaintThing.editLookup.put( MiniFacade.class, MiniFacadePainter.class );
	}
	
	private Object readResolve() {
		if ( facadeLabelApp == null )
			facadeLabelApp = new FacadeLabelApp( this );
		if ( facadeTexApp == null )
			facadeTexApp = new FacadeTexApp( this );
		if ( facadeSuperApp == null )
			facadeSuperApp = new FacadeSuperApp( this );
		if ( facadeGreebleApp == null )
			facadeGreebleApp = new FacadeGreebleApp( this );
		return this;
	}
	  
	public MiniFacade( MiniFacade m ) {
		
		this.width = m.width;
		this.height = m.height;
		this.groundFloorHeight = m.groundFloorHeight;
		this.left = m.left;
		this.softLeft = m.softLeft;
		this.softRight = m.softRight;
		this.imageFeatures = m.imageFeatures;
		this.imageXM = m.imageXM;
		this.scale = m.scale;
		this.featureGen = m.featureGen.copy(this);
		this.hMargin = new ArrayList(m.hMargin);
		this.vMargin = new ArrayList(m.vMargin);
		this.sf = m.sf;
		
		if (m.grid != null)
			this.grid = new WinGrid ( m.grid ); 
	}

	
	public MiniFacade(){
	}
	
	public MiniFacade (
			
			ImageFeatures ifs,
			Map yaml, 
			double imageWidthM, 
			double imageHeightM,
			double scale,
			double imageXM
			) {
		
		this.imageFeatures = ifs;
		this.imageXM = imageXM;
		this.scale = scale;
		
		double topM = imageHeightM;
		
		        left =  Double.parseDouble( (String) yaml.get( "facade-left"  ) ) / scale + imageXM;
		double right =  Double.parseDouble( (String) yaml.get( "facade-right" ) ) / scale + imageXM;
		
		width = right-left;
		
		{
			double softTol = TweedSettings.settings.miniSoftTol;
			
			double imgRight = imageXM + imageWidthM;

			softLeft  = imgRight - left  < softTol || left  - imageXM < softTol;
			softRight = imgRight - right < softTol || right - imageXM < softTol;
		}
		
		height            = topM - Double.parseDouble( (String) yaml.get( "sky-line"   ) ) / scale;
		groundFloorHeight = topM - Double.parseDouble( (String) yaml.get( "door-line"  ) ) / scale;
		
		List<String> rgb = (List) yaml.get( "rgb" );
		
		wallColor = new Color( 
				Float.parseFloat ( rgb.get( 0 ) ),
				Float.parseFloat ( rgb.get( 1 ) ),
				Float.parseFloat ( rgb.get( 2 ) ) );
		
		readRegions ( scale, imageXM, topM, ((Map) yaml.get("regions")) );
//		hMargin = readMargin( ((List) yaml.get("win_h_margin")) );
//		vMargin = readMargin( ((List) yaml.get("win_v_margin")) );
		readWinGrid( scale, imageXM, topM, ((Map) yaml.get("window-grid")) );
		
//		if (yaml.get("mezzanine") != null) {
//			List<String> col = (List) ((Map)yaml.get( "mezzanine" )).get("rgb");
//			groundColor = new double[] {
//					Double.parseDouble ( col.get( 0 ) ),
//					Double.parseDouble ( col.get( 1 ) ),
//					Double.parseDouble ( col.get( 2 ) ), 1 };
//		}
		
		
	}
	
	public boolean invalid() {
		return softLeft && softRight && width < 2;
	}

	private List<Double> readMargin( List<String> map ) {
		
		List<Double> out = new ArrayList<>();
		
		for (String s : map)
			out.add (Double.parseDouble( s )); 
		
		return out;
	}

	private void readRegions( double scale, double imageXM, double topM, Map map ) {
		
		if (map == null)
			map = new HashMap<>();
		
		for (Feature f : Feature.values()) 
			featureGen.putAll (f,readRects (scale, imageXM, topM, map, f.name().toLowerCase() ), false );
	}

	private List<FRect> readRects( double scale, double imageXM, double topM, Map<String, List<Map>>map, String name ) {
		
		List<FRect> out = new ArrayList<>();
		
		if (!map.containsKey( name ))
			return out;
		
		for (Map r : map.get(name)) {
			
			double h = (Double.parseDouble( (String) r.get( "bottom" ) ) - Double.parseDouble( (String) r.get( "top"  ) )) / scale;
			
			out.add (new FRect(
					Double.parseDouble( (String) r.get( "left"   ) ) / scale + left,
				    topM - Double.parseDouble( (String) r.get( "bottom"    ) ) / scale,
 				   (Double.parseDouble( (String) r.get( "right"  ) ) - Double.parseDouble( (String) r.get( "left" ) )) / scale,
				   h, this
					));
		}
		
		return out;
		
	}

	public static class WinGrid {
		
		
		public WinGrid(){}
		
		public WinGrid( WinGrid o ) {
			this.x = o.x;
			this.y = o.y;
			this.width = o.width;
			this.height = o.height;
			this.wHeight = o.wHeight;
			this.wWidth = o.wWidth;
			this.vspacing = o.vspacing;
			this.hspacing = o.hspacing;
			this.cols = o.cols;
			this.rows = o.cols;
		}
		
		List<Win> dows = new ArrayList<>();// deleteme
		
		double x, y, width, height, wHeight, wWidth, vspacing, hspacing;
		int cols, rows;
	}
	
	public static class Win { //deleteme
		public double x, y, width, height;
		public Win(){}
		public Win (double x, double y, double width, double height) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}
	}
	
	private void readWinGrid( double scale, double imageXM, double topM, Map yGrid ) {
		{
			
			if ( yGrid == null || yGrid.get( "bottom" ).equals( "-1" ) )
				return;
			
			grid = new WinGrid();
			
			grid.x       = Double.parseDouble( (String) yGrid.get( "left"   ) ) / scale + imageXM;
			double gr = Double.parseDouble( (String) yGrid.get( "right"  ) ) / scale + imageXM;
			grid.width = gr - grid.x;
			
			grid.y       = topM - Double.parseDouble( (String) yGrid.get( "bottom"    ) ) / scale;
			double gt = topM - Double.parseDouble( (String) yGrid.get( "top" ) ) / scale;
			grid.height = gt - grid.y;
			
			grid.wHeight = Double.parseDouble( (String) yGrid.get( "height"  ) ) / scale;
			grid.wWidth  = Double.parseDouble( (String) yGrid.get( "width"   ) ) / scale;
			grid.vspacing  = scale ( yGrid, scale, "vertical_spacing" );
			grid.hspacing  = scale ( yGrid, scale, "horizontal_spacing" );
			
			grid.cols = Integer.parseInt( (String) yGrid.get( "cols" ) );
			grid.rows = Integer.parseInt( (String) yGrid.get( "rows" ) );
			
			for (Object wp : (List) yGrid.get("rectangles") ) {
				
				List<Double> dp = ((List<String>)wp).stream().map(x -> Double.parseDouble(x)).collect( Collectors.toList() );
				
				FRect win = new FRect(this);
				
				win.setFeat( Feature.GRID );
				win.x      = dp.get(1) / scale + imageXM;
				win.y      = topM - dp.get(2) / scale;
				win.width  = (dp.get(3) / scale + imageXM) - win.x;
				win.height = (topM - dp.get(0) / scale) - win.y;
				
				featureGen.put( win.getFeat(), win );
			}
		}
	}

	private double scale( Map yGrid, double scale, String key ) {
		try {
			return  Double.parseDouble( (String) yGrid.get( key ) ) / scale;
		}
		catch (NumberFormatException e) {
			return 0;
		}
	}

	public java.awt.geom.Rectangle2D.Double toRect() {
		return new Rectangle2D.Double( left, 0, width, height );
	}

	public void scaleX( double lp, double rp ) {
		
		if (!softLeft && !softRight) 
			scaleX (new DRectangle(lp, -1, rp-lp, -1));
		else if (softLeft) 
			scaleX(new DRectangle(rp - width, -1, width, -1) );
		else if (softRight) 
			scaleX(new DRectangle(lp, -1, width, -1) );
		else 
			throw new Error("no way to ground scale!");
	}
	
	public void scaleX2( double lp, double rp ) {
		scaleX (new DRectangle(lp, -1, rp-lp, -1));
	}
	
	public void scaleX( DRectangle all ) {
		
		double xFactor = all.width / width;
		
		for (Feature f : Feature.values())  
			featureGen.map.put( f, scale ( featureGen.get(f), xFactor, all.x-left ) );
		
		
//		if (grid != null) {
//			
//			List<DRectangle> scaled = new ArrayList<>();
//			
//		}
		
		width = all.width;
		left = all.x;
	}

	private List<FRect> scale( List<FRect> rects, double widthRatio, double xOffset ) {
		
		List<FRect> out = new ArrayList();
		if ( rects != null )
			for ( FRect o : rects ) {
				FRect n = new FRect( o );
				n.width *= widthRatio;
				n.x = (n.x - left) * widthRatio + left + xOffset;
				out.add( n );
			}
		
		return out;
	}



	public double right() {
		return left + width;
	}
	
	public double left() {
		return left;
	}
	
	public double center() {
		return left + width/2;
	}
	
	public DRectangle getAsRect() {
		return new DRectangle(left, 0, width, height);
	}
	
	public boolean contains( Point2d avg ) {
		return getAsRect().contains( avg );
	}
	
	public BufferedImage render( double res, Feature... fs ) {
		BufferedImage bi = new BufferedImage( (int) ( res * width ), (int) ( res * height ), BufferedImage.TYPE_3BYTE_BGR );

		Graphics g = bi.getGraphics();

		g.setColor( Color.black );
		g.fillRect( 0, 0,  bi.getWidth(),  bi.getHeight() );
		
		if ( fs.length == 0 ) 
		{
			BufferedImage source = imageFeatures.getRectified();

			if ( source != null ) {
				g.drawImage( source,
						0, 0, bi.getWidth(), bi.getHeight(),
						(int) ( ( left - imageXM ) * scale ), source.getHeight() - (int) ( ( height ) * scale ), (int) ( ( left + width - imageXM ) * scale ), source.getHeight(), null );
			}
		}
		else
		{
			for ( Feature f : fs ) {

				g.setColor( f.color );

				for ( FRect w : featureGen.get( f ) ) {
					if ( w.width * w.height > 0.2 ) {
						g.fillRect( (int) ( res * ( w.x - left ) ), bi.getHeight() - (int)( res * ( w.y + w.height) ), (int) ( res * w.width ), (int) ( res * w.height ) );
					}
				}
			}
		}

		g.dispose();

		return bi;
	}


	@Override
	public SuperSuper getSuper() {
		return facadeSuperApp;
	}


	public static MiniFacade newWithApps( MiniFacade miniFacade ) {
		MiniFacade out = new MiniFacade(miniFacade);
		
		out.facadeGreebleApp = new FacadeGreebleApp( miniFacade.facadeGreebleApp );
		out.facadeGreebleApp.mf = out;
		
		out.facadeLabelApp = new FacadeLabelApp( miniFacade.facadeLabelApp );
		out.facadeLabelApp.mf = out;
		
		out.facadeTexApp = new FacadeTexApp( miniFacade.facadeTexApp );
		out.facadeTexApp.mf = out;
		
		
		
		return out;
	}
}
