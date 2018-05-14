package org.twak.viewTrace.franken;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;

import org.twak.tweed.TweedFrame;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.ui.AutoEnumCombo;
import org.twak.utils.ui.AutoEnumCombo.ValueSet;
import org.twak.utils.ui.ColourPicker;
import org.twak.utils.ui.FileDrop;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.facades.NSliders;
import org.twak.viewTrace.facades.Pix2Pix;
import org.twak.viewTrace.franken.App.AppMode;


public class SelectedApps extends ArrayList<App>{
	
	App exemplar;
	
	public SelectedApps(App app) {
		add(app);
		exemplar = app;
	}
	
	public SelectedApps() {}
	
	public SelectedApps( List<App> list ) {
		super (list);
		exemplar = list.get( 0 );
	}

	@Override
	public boolean add( App e ) {
		if (exemplar == null)
			exemplar= e;
		return super.add( e );
	}
	
	private SelectedApps findUp() {
		
		SelectedApps sa = new SelectedApps();
		
		for (App a : this) {
			App up = a.getUp();
			if (up != null)
				sa.add(up);
		}
		
		return sa;
	}
	
	private Map<String, SelectedApps> findDown () {
		Map<String, SelectedApps> out = new LinkedHashMap<>();
		for (App a : this) {
			MultiMap<String, App> as = a.getDown();
			if (as != null)
			for ( String name : as.keySet() ) 
				out.put( name, new SelectedApps(as.get( name )) );
		}
		return out;
	}
	
	private void computeAll(Runnable globaUpdate) {
		computeAll_( globaUpdate, 0 );
	}
	private void computeAll_(Runnable globalUpdate, int i) {
		if (i >= size())
			globalUpdate.run();
		else
			get(i).computeWithChildren( globalUpdate, 
					new Runnable() {
						@Override
						public void run() {
							computeAll_( globalUpdate, i + 1 );
						}
					} );
	}
	
	public JComponent createUI( Runnable globalUpdate ) {

		JPanel top = new JPanel(new ListDownLayout() );
		JPanel main = new JPanel(new BorderLayout() );
		
		JPanel options = new JPanel();

		top.add( new JLabel( exemplar.name +" ("+size()+" selected)"), BorderLayout.NORTH );
		
		JPanel upDown = new JPanel(new FlowLayout(FlowLayout.TRAILING));
		
		SelectedApps ups = findUp();
		
		if ( !ups.isEmpty() ) {
			JButton up   = new JButton("↑" + ups.exemplar.name);
			up.addActionListener( e -> TweedFrame.instance.tweed.frame.setGenUI( findUp().createUI ( globalUpdate) ));
			upDown.add( up, BorderLayout.WEST);
		}
		
		Map<String, SelectedApps> downs = findDown();
		
		if (downs != null)
		for (String wayDown : downs.keySet()) {
			JButton down = new JButton("↓ "+wayDown+"("+downs.get( wayDown ).size()+")");
			upDown.add( down, BorderLayout.EAST);
			down.addActionListener( e -> TweedFrame.instance.tweed.frame.setGenUI( downs.get( wayDown ).createUI ( globalUpdate) ) );
		}
		
		top.add(upDown);
		
		AutoEnumCombo combo = new AutoEnumCombo( exemplar.appMode, new ValueSet() {
			public void valueSet( Enum num ) {
				
				for (App a : SelectedApps.this)
					a.appMode = (AppMode) num;
				
				options.removeAll();
				
				options.setLayout( new ListDownLayout() );

				buildLayout(exemplar.appMode, options, () -> SelectedApps.this.computeAll( globalUpdate ) );
				
				options.repaint();
				options.revalidate();
				
				new Thread("combo app select") {
					public void run() {
						SelectedApps.this.computeAll( globalUpdate );
					};
				}.start();
			}
		} );
		buildLayout(exemplar.appMode, options, () -> SelectedApps.this.computeAll( globalUpdate ) );
		
		top.add(combo);
		
		main.add( top, BorderLayout.NORTH );
		main.add( options, BorderLayout.CENTER );

		return main;
	}

	private void buildLayout( AppMode appMode, JPanel out, Runnable whenDone ) {
		
		for (App a : this)
			a.appMode = appMode;
		
		switch (appMode) {
		case Color:
			JButton col = new JButton("color");
			
			for (App a : SelectedApps.this) {
				a.texture = null;
			}
			
			col.addActionListener( e -> new ColourPicker(null, exemplar.color) {
				@Override
				public void picked( Color color ) {
					for (App a : SelectedApps.this) {
						a.color = color;
						a.texture = null;
					}
					whenDone.run();
				}
			} );
			out.add( col );
			break;
		case Bitmap:
		default:
			out.add( new JLabel("no options") );
			break;
		case Net:

			NSliders sliders = new NSliders( exemplar.styleZ, new Runnable() {
				
				@Override
				public void run() {
					for (App a : SelectedApps.this)
						a.styleZ = exemplar.styleZ;
					whenDone.run();
				}
			} );
			
			FileDrop drop = new FileDrop( "style" ) {
				public void process(java.io.File f) {
					new Pix2Pix().encode( f, exemplar.resolution, exemplar.netName, exemplar.styleZ, new Runnable() {
						@Override
						public void run() {
//							for (App a : SelectedApps.this)
//								a.styleZ = exemplar.styleZ;
							
							sliders.setValues (exemplar.styleZ);
							
//							whenDone.run();
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
