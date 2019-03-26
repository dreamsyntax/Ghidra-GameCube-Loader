package gamecubeloader.dol;

import java.io.FileReader;

import docking.widgets.OptionDialog;
import docking.widgets.filechooser.GhidraFileChooser;
import gamecubeloader.common.SymbolLoader;
import gamecubeloader.dol.DOLHeader;
import ghidra.app.util.MemoryBlockUtil;
import ghidra.app.util.bin.ByteProvider;
import ghidra.app.util.importer.MemoryConflictHandler;
import ghidra.program.model.address.AddressSpace;
import ghidra.program.model.listing.Program;
import ghidra.util.Msg;
import ghidra.util.filechooser.ExtensionFileFilter;
import ghidra.util.task.TaskMonitor;

public final class DOLProgramBuilder {
	private DOLHeader dol;
	
	private long baseAddress;
	private AddressSpace addressSpace;
	private Program program;
	private MemoryBlockUtil memoryBlockUtil;
	
	public DOLProgramBuilder(DOLHeader dol, ByteProvider provider, Program program,
			MemoryConflictHandler memConflictHandler, TaskMonitor monitor) {
		this.dol = dol;
		this.program = program;
		this.memoryBlockUtil = new MemoryBlockUtil(program, memConflictHandler);
		
		this.load(monitor, provider);
	}
	
	protected void load(TaskMonitor monitor, ByteProvider provider) {
		this.baseAddress = 0x80000000L;
		this.addressSpace = program.getAddressFactory().getDefaultAddressSpace();
		
		try {
			this.program.setImageBase(addressSpace.getAddress(this.baseAddress), true);
			
			// Load the DOL file.
			for (int i = 0; i < 7; i++) {
				if (dol.textSectionSizes[i] > 0) {
					memoryBlockUtil.createInitializedBlock(DOLHeader.TEXT_NAMES[i], addressSpace.getAddress(dol.textSectionMemoryAddresses[i]),
						provider.getInputStream(dol.textSectionOffsets[i]), dol.textSectionSizes[i], "", null, true, true, true, monitor);
				}
			}
			
			for (int i = 0; i < 11; i++) {
				if (dol.dataSectionSizes[i] > 0) {
					memoryBlockUtil.createInitializedBlock(DOLHeader.DATA_NAMES[i], addressSpace.getAddress(dol.dataSectionMemoryAddresses[i]),
						provider.getInputStream(dol.dataSectionOffsets[i]), dol.dataSectionSizes[i], "", null, true, true, false, monitor);
				}
			}
			
			// Add .bss sections.
			var bssSectionSize = dol.dataSectionMemoryAddresses[6] - dol.bssMemoryAddress;
			var bss = memoryBlockUtil.createUninitializedBlock(false, ".bss", addressSpace.getAddress(dol.bssMemoryAddress), bssSectionSize, "", null, true, true, false);
			if (bss == null) {
				Msg.info(this, "bss section creation failed!");
				Msg.info(this, memoryBlockUtil.getMessages());
			}
			
			// Check if we need to add a .sbss section.
			if (bssSectionSize + dol.dataSectionSizes[6] < dol.bssSize) {			
				var sbssSectionAddress = dol.dataSectionMemoryAddresses[6] + dol.dataSectionSizes[6];
				var sbssSectionSize = dol.dataSectionMemoryAddresses[7] - sbssSectionAddress;
				var sbss = memoryBlockUtil.createUninitializedBlock(false, ".sbss", addressSpace.getAddress(sbssSectionAddress), sbssSectionSize, "", null, true, true, false);
				if (sbss == null) {
					Msg.info(this, "sbss section creation failed!");
					Msg.info(this, memoryBlockUtil.getMessages());
				}
				
				// TODO: .sdata2 & .sbss2 are odd. They're not included in the uninitialized sections size in AC, but .sdata2 does exist. How is this handled?
			}
			
			// Ask if the user wants to load a symbol map file.
			if (OptionDialog.showOptionNoCancelDialog(null, "Load Symbols?", "Would you like to load a symbol map for this file?", "Yes", "No", null) == 1) {
				var fileChooser = new GhidraFileChooser(null);
				fileChooser.setCurrentDirectory(provider.getFile().getParentFile());
				fileChooser.addFileFilter(new ExtensionFileFilter("map", "Symbol Map Files"));
				var selectedFile = fileChooser.getSelectedFile(true);
				
				if (selectedFile != null) {
					FileReader reader = new FileReader(selectedFile);
					SymbolLoader loader = new SymbolLoader(this.program, monitor, reader, dol.textSectionMemoryAddresses[0], 32, dol.bssMemoryAddress);
					loader.ApplySymbols();
				}
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	

}
