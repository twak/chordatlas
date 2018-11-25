package org.twak.viewTrace.franken;

import java.awt.Color;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.twak.utils.ui.AutoCheckbox;
import org.twak.utils.ui.ColourPicker;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.facades.FRect;
import org.twak.viewTrace.franken.App.TextureMode;

public class DoorTexApp extends PanesTexApp {
	
	private final static Color DEFAULT_COLOR = new Color (0,0,100);
	
	public DoorTexApp(FRect fr ) {
		super( fr );
		color = DEFAULT_COLOR;
	}
	
	public DoorTexApp(DoorTexApp t) {
		super ( t );
		color = DEFAULT_COLOR;
	}
	
	@Override
	public JComponent createUI( Runnable globalUpdate, SelectedApps apps ) {

		JPanel out = new JPanel(new ListDownLayout() );

		if ( appMode == TextureMode.Off ) {
			
			JButton col = new JButton( "colour" );

			col.addActionListener( e -> new ColourPicker( null, color ) {
				@Override
				public void picked( Color color ) {

					for ( App a : apps ) {
						DoorTexApp da = ( (DoorTexApp) a );
						da.color = color;
						da.fr.panesLabelApp.texture = null;
						da.appMode = da.fr.panesLabelApp.appMode = TextureMode.Off;
					}

					globalUpdate.run();
				}
			} );

			out.add( col );
		}

		return out;
	}
	
	public App copy() {
		return new DoorTexApp( this );
	}
}
