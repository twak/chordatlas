package org.twak.tweed.gen;

import java.io.File;

public interface ICanSave {
	default public boolean canISave() {
		return true;
	}
}
