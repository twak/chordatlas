package org.twak.viewTrace.franken.style.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.border.EmptyBorder;

import org.twak.utils.Mathz;
import org.twak.utils.Stringz;
import org.twak.utils.ui.AutoDoubleSlider;
import org.twak.utils.ui.AutoListCombo;
import org.twak.utils.ui.AutoTextField;
import org.twak.utils.ui.ListDownLayout;
import org.twak.utils.ui.ListRightLayout;
import org.twak.utils.ui.SimpleFileChooser;
import org.twak.utils.ui.WindowManager;
import org.twak.viewTrace.franken.BlockApp;
import org.twak.viewTrace.franken.NetInfo;
import org.twak.viewTrace.franken.style.JointDistribution;
import org.twak.viewTrace.franken.style.JointDistribution.Joint;
import org.twak.viewTrace.franken.style.JointDistribution.NetProperties;

import com.thoughtworks.xstream.XStream;


public class JointUI extends JPanel {

	private JointDistribution jd;
	private Joint selectedJoint;
	JFrame frame;
	Runnable globalUpdate;
	
	
	JPanel modalPanel;
	MultiModalEditor modal;
	
	public JointUI (JointDistribution jd, Runnable globalUpdate) {
		
		this.jd = jd;
		
		if (jd.joints.isEmpty())
			jd.rollJoint();
		
		this.globalUpdate = globalUpdate;
//		Runnable() {
//			
//			@Override
//			public void run() {
//				jd.redraw();
//				globalUpdate.run();		
//			}
//		};
		
		this.selectedJoint = jd.joints.get(0);
		buildNetSelectUI();
	}
	
	private void selectJoint( Joint j ) {
		if (j == selectedJoint)
			return;
		
		this.selectedJoint = j;
		buildNetSelectUI();
	}

	public void netSelected( NetProperties ns ) {
		
		modalPanel.removeAll();
		
		if (modal != null)
			modal.stop();
		
		Runnable modalUpdate = new Runnable() {
			@Override
			public void run() {
				jd.redraw();
				globalUpdate.run();
			}
		};
		
		modalPanel.add (modal = new MultiModalEditor( 
				selectedJoint.appInfo.get( ns.klass ).dist, NetInfo.index.get( ns.klass ), modalUpdate ), 
				BorderLayout.CENTER );
		
		modalPanel.revalidate();
	}

	public void buildNetSelectUI () {
		
		removeAll();
		
		JPanel top = new JPanel (new BorderLayout());
		
		top.add ( jointUI(), BorderLayout.WEST   );
		top.add ( netsUI() , BorderLayout.CENTER );
		
//		top.setPreferredSize( new Dimension( top.getPreferredSize().width, 100 ) );
		
		top.setBorder( BorderFactory.createMatteBorder( 0, 0, 4, 0, Color.black ) );
		
		setLayout( new BorderLayout() );
		add (top, BorderLayout.NORTH);
		add (modalPanel = new JPanel(new BorderLayout()), BorderLayout.CENTER);
		
		JButton close = new JButton( "ok" );
		close.addActionListener( l -> frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)) );
		add( close, BorderLayout.SOUTH );

		selectedJoint = jd.joints.get( 0 );
		netSelected( jd.defaultNet );
	}
	

	public Component createNetUI(NetProperties ns, JointUI ui, boolean selected, ButtonGroup bg) {
		
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder( MultiModalEditor.BORDER );
		
		JToggleButton select = new JToggleButton ( Stringz.splitCamelCase( ns.klass.getSimpleName() ).replaceAll( " App", "" ) );
		bg.add( select );
		
		select.setSelected( selected );
		select.addActionListener(  e -> netSelected (ns) );
		
//		panel.setPreferredSize( new Dimension (140, 40) );

		JPanel northPanel = new JPanel(new BorderLayout());
		
		JToggleButton on = new JToggleButton("on");
		on.setSelected( ns.on );
		on.addActionListener( e -> ns.on = on.isSelected() );
		
		northPanel.add (select, BorderLayout.CENTER );
		northPanel.add( on, BorderLayout.EAST );
		
		panel.add (northPanel, BorderLayout.NORTH );
		
		
		List<Class> options = new ArrayList<>();
		
		for ( NetProperties np : jd.nets)
			if (np.klass != BlockApp.class )
				options.add( np.klass );
		
		AutoListCombo<Class> lc = new AutoListCombo<Class> ( selectedJoint.appInfo.get( ns.klass ), "bakeWith", "fixZ", options ) {
			
			public void fire(Class e) {
				jd.redraw();
			}

			@Override
			public String getName(Class o2) {
				return Stringz.splitCamelCase( o2.getSimpleName() ).replaceAll( " App", "" ).toLowerCase();
			};
		};
		
		panel.setPreferredSize( new Dimension( 180, panel.getPreferredSize().height ) );
		panel.add(lc, BorderLayout.CENTER);
		
		return panel;
	}
	
	private JPanel netsUI() {
		
		JPanel out = new JPanel( new ListRightLayout() );

		ButtonGroup netBG;
		netBG = new ButtonGroup();
		for (NetProperties c : jd.nets) {
			if (c.show)
				out.add( createNetUI( c, this, c == jd.defaultNet, netBG) );
		}
		
		return out;
	}

	public JPanel jointUI() {
		
		JPanel panel = new JPanel();
		
		panel.setLayout( new ListDownLayout() );
		
		
		AutoTextField atf = new AutoTextField(selectedJoint, "name", "name");
		
		panel.add( atf );
		
		JPanel newDelete = new JPanel(new GridLayout( 1, 4 ));
		
		JButton n = new JButton("+");
		n.addActionListener( e -> { Joint j = jd.rollJoint(); selectJoint( j ); } );
		
		JButton d = new JButton("-");
		d.addActionListener( e -> killJoint() );
		
		JButton lt = new JButton("<");
		lt.addActionListener( e -> deltaJoint(-1) );
		JButton gt = new JButton(">");
		gt.addActionListener( e -> deltaJoint(1) );
		
		AutoDoubleSlider prob = new AutoDoubleSlider( selectedJoint, "probability", "p", 0, 1 ) {
			public void updated( double p ) {
				selectedJoint.probability = p;
				jd.updateJointProb();
			}
		};
		
		newDelete.add(n);
		newDelete.add(d);
		newDelete.add(lt);
		newDelete.add(gt);

		if (selectedJoint == null) {
			d.setEnabled( false );
			lt.setEnabled( false );
			gt.setEnabled( false );
			atf.text.setEditable( false );
		}
		
		panel.add(newDelete);
		panel.add(prob);
		
		panel.setPreferredSize( new Dimension (150, (int) panel.getPreferredSize().getHeight()) );
		
		panel.setBorder( new EmptyBorder( 2, 2, 2, 2 ) );
		
		return panel;
	}

	private void deltaJoint( int i ) {
		
		int si= 0;
		
		
		if (selectedJoint != null)
			si = jd.joints.indexOf( selectedJoint ) ;
		
		si = ( si + i + jd.joints.size() ) % jd.joints.size();
		
		selectJoint( jd.joints.get(si) );
	}

	private void killJoint() {
		
		if ( jd.joints.size() == 1 ) {
			JOptionPane.showMessageDialog( frame, "can't delete only distribution" );
			return;
		}
		
		int si = jd.joints.indexOf( selectedJoint ) ;
		jd.joints.remove (selectedJoint);
		jd.updateJointProb();
		
		selectJoint( jd.joints.get (Mathz.clamp ( si + 1, 0, jd.joints.size()-1  )) );
	}
	
	public void openFrame() {
		
		frame = WindowManager.frame( "joint distriubtion editor",  this);
		frame.setResizable( false );
		
		frame.addWindowListener( new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				
				if (modal != null)
					modal.stop();
				
				jd.redraw();
				globalUpdate.run();
			};
		} );
		
		
		JMenuBar bar = new JMenuBar();
		
		JMenu ls = new JMenu("file");
		bar.add( ls );
		
		JMenuItem load = new JMenuItem("load...");
		load.addActionListener( new ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {
				
				System.out.println("here");
				new SimpleFileChooser(frame) {
					@Override
					public void heresTheFile( File f ) throws Throwable {
						BlockApp editing = jd.root;
						try {
						jd = (JointDistribution) new XStream().fromXML( f );
						jd.root = editing;
						jd.redraw();
						buildNetSelectUI();
						}
						catch (Throwable th) {
							JOptionPane.showMessageDialog( frame, "an error occured while loading " + f.getName() );
						}
					}
				};
			}
		});
		ls.add( load );
		
		JMenuItem save = new JMenuItem("save...");
		save.addActionListener( new ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {
				new SimpleFileChooser(frame, true, "select file to save to", null, "xml") {
					@Override
					public void heresTheFile( File f ) throws Throwable {
//						f = new File (f.getParentFile(), Filez.stripExtn( f.getName() ) +".xml");
						new XStream().toXML( jd, new FileOutputStream( f ) );
					}
				};
			}
		});
		ls.add( save );
		frame.setJMenuBar( bar );
		
		frame.pack();
		frame.setVisible( true );
	}
}
