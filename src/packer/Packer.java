package packer;

import java.io.IOException;

/**
 * Rice Hi-Res texture packer for Wii64
 * 
 * @author emu_kidid
 */

public class Packer {

	private static Archiver	archiver	= null;

	public static void main(String[] argv) {
		try {
			archiver = new Archiver();
			archiver.setVisible(true);
			while (archiver.isVisible()) {
				Thread.sleep(100);
			}
		} catch (IOException e) {
			System.out.println("Error Creating output archive to: " + archiver.getPakPath());
		} catch (InterruptedException e) {
		}
	}

}
