package org.twak.viewTrace.franken;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.twak.tweed.TweedFrame;
import org.twak.utils.collections.MultiMap;
import org.twak.utils.ui.AutoEnumCombo;
import org.twak.utils.ui.AutoEnumCombo.ValueSet;
import org.twak.utils.ui.ColourPicker;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.franken.App.AppMode;
import org.twak.viewTrace.franken.style.ConstantStyle;
import org.twak.viewTrace.franken.style.GaussStyle;
import org.twak.viewTrace.franken.style.StyleSource;


public class SelectedApps extends ArrayList<App>{
	
	public App exemplar;
	
	public SelectedApps(App app) {
		add(app);
		exemplar = app;
	}
	
	public SelectedApps() {}
	
	public SelectedApps( List<App> list ) {
		super (new ArrayList (new LinkedHashSet<>(list) ) );
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
		MultiMap<String, App> as = new MultiMap<>();
		
		for (App a : this) 
			as.putAll( a.getDown() );
		
		Map<String, SelectedApps> out = new LinkedHashMap<>();
		for (String name : as.keySet())
			out.put( name, new SelectedApps(as.get( name ) ) );
			
		return out;
	}
	
	public void computeAll(Runnable globaUpdate) {
		computeAll_( globaUpdate, 0 );
	}
	private void computeAll_(Runnable globalUpdate, int i) {
		App.computeWithChildren( this, 0, globalUpdate );
	}
	
	public JPanel createUI( Runnable update_ ) {

		Runnable update = new Runnable() {
			@Override
			public void run() {
				for (App a : SelectedApps.this)
					a.markDirty();
				update_.run();
			}
		};
		
		JPanel top = new JPanel(new ListDownLayout() );
		JPanel main = new JPanel(new BorderLayout() );
		
		JPanel options = new JPanel();

		top.add( new JLabel( exemplar.name +" ("+size()+" selected)"), BorderLayout.NORTH );
		
		JPanel upDown = new JPanel(new FlowLayout(FlowLayout.TRAILING));
		
		SelectedApps ups = findUp();
		
		if ( !ups.isEmpty() ) {
			JButton up   = new JButton("↑" + ups.exemplar.name);
			up.addActionListener( e -> TweedFrame.instance.tweed.frame.setGenUI( findUp().createUI ( update) ));
			upDown.add( up, BorderLayout.WEST);
		}
		
		Map<String, SelectedApps> downs = findDown();
		
		if (downs != null)
		for (String wayDown : downs.keySet()) {
			JButton down = new JButton("↓ "+wayDown+"("+downs.get( wayDown ).size()+")");
			upDown.add( down, BorderLayout.EAST);
			down.addActionListener( e -> TweedFrame.instance.tweed.frame.setGenUI( downs.get( wayDown ).createUI ( update) ) );
		}
		
		top.add(upDown);
		
		options.setLayout( new ListDownLayout() );
		
		AutoEnumCombo combo = new AutoEnumCombo( exemplar.appMode, new ValueSet() {
			public void valueSet( Enum num ) {
				
				for (App a : SelectedApps.this)
					a.appMode = (AppMode) num;
				
				options.removeAll();
				options.setLayout( new ListDownLayout() );

				buildLayout(exemplar.appMode, options, () -> refresh ( update ) );
				
				options.repaint();
				options.revalidate();
				
				new Thread("combo app select") {
					public void run() {
						refresh( update );
					};
				}.start();
			}
		}, "texture", exemplar.getValidAppModes() );
		buildLayout(exemplar.appMode, options, () -> refresh( update ) );
		
		if (exemplar.resolution > 0)
			top.add(combo); // building doesn't have options yet
		
		main.add( top, BorderLayout.NORTH );
		main.add( options, BorderLayout.CENTER );

		return main;
	}

	protected void refresh( Runnable update ) {
		new Thread ( () -> SelectedApps.this.computeAll(update) ).start();
	}

	private void buildLayout( AppMode appMode, JPanel out, Runnable update ) {
		
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
					update.run();
				}
			} );
			out.add( col );
			break;
		case Bitmap:
		default:
			out.add( new JLabel("no options") );
			break;
		case Net:
			out.add( createDistEditor(update) );
			
			break;
		}
	}

	private enum StyleSources {
		Gaussian (GaussStyle.class), Constant (ConstantStyle.class);
		
		Class<? extends StyleSource> klass;
		
		StyleSources (Class<?> klass) {
			this.klass = (Class<? extends StyleSource> ) klass;
		}
		
		public StyleSource instance(App app) {
			try {
				return klass.getConstructor( App.class ).newInstance( app );
			} catch ( Throwable e ) {
				e.printStackTrace();
				return null;
			}
		}
	}
	
	static Map<Class, StyleSources> ss2Klass = new HashMap<>();
	static {
		for (StyleSources ss : StyleSources.values()) 
			ss2Klass.put( ss.klass, ss );
	}
	
	private Component createDistEditor( Runnable update ) {
		
		JPanel out = new JPanel( new BorderLayout() );
		JPanel options = new JPanel();
		
		AutoEnumCombo combo = new AutoEnumCombo( ss2Klass.get(exemplar.styleSource.getClass()), new ValueSet() {
			public void valueSet( Enum num ) {
				
				StyleSources sss = (StyleSources) num;
				StyleSource ss;

				if (exemplar.styleSource.getClass() == sss.klass)
					ss = exemplar.styleSource;
				else
					ss = sss.instance(exemplar);

				boolean changed = false;
				for (App a : SelectedApps.this) {
					changed |= a.styleSource != ss;
					a.styleSource = ss;
				}
				
				options.removeAll();
				options.setLayout( new BorderLayout() );
				options.add( ss.getUI(update), BorderLayout.CENTER );
				options.repaint();
				options.revalidate();
				
				if (changed)
					update.run();
			}
		}, "distribution:" );
		
		combo.fire();
		
		out.add( combo, BorderLayout.NORTH );
		out.add( options, BorderLayout.CENTER );
		
		return out;
	}
}
