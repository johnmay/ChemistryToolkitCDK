/*******************************************************************************
 * Copyright C 2015, The Pistoia Alliance
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 ******************************************************************************/
package org.helm.chemtoolkit.cdk;
/**
 * @author chistyakov
 *
 */

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageOutputStream;

import org.helm.chemtoolkit.AbstractChemistryManipulator;
import org.helm.chemtoolkit.AbstractMolecule;
import org.helm.chemtoolkit.AttachmentList;
import org.helm.chemtoolkit.CTKException;
import org.helm.chemtoolkit.CTKSmilesException;
import org.helm.chemtoolkit.IAtomBase;
import org.helm.chemtoolkit.IBondBase;
import org.helm.chemtoolkit.IStereoElementBase;
import org.helm.chemtoolkit.MoleculeInfo;
import org.openscience.cdk.depict.DepictionGenerator;
import org.openscience.cdk.interfaces.IChemObjectBuilder;
import org.openscience.cdk.silent.Bond;
import org.openscience.cdk.aromaticity.Aromaticity;
import org.openscience.cdk.aromaticity.ElectronDonation;
import org.openscience.cdk.atomtype.CDKAtomTypeMatcher;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.openscience.cdk.graph.CycleFinder;
import org.openscience.cdk.graph.Cycles;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IAtomType;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.interfaces.IBond.Stereo;
import org.openscience.cdk.interfaces.IPseudoAtom;
import org.openscience.cdk.interfaces.IStereoElement;
import org.openscience.cdk.interfaces.ITetrahedralChirality;
import org.openscience.cdk.io.MDLV2000Reader;
import org.openscience.cdk.io.MDLV2000Writer;
import org.openscience.cdk.layout.StructureDiagramGenerator;
import org.openscience.cdk.renderer.AtomContainerRenderer;
import org.openscience.cdk.renderer.font.AWTFontManager;
import org.openscience.cdk.renderer.generators.BasicAtomGenerator;
import org.openscience.cdk.renderer.generators.BasicBondGenerator;
import org.openscience.cdk.renderer.generators.BasicSceneGenerator;
import org.openscience.cdk.renderer.generators.ExtendedAtomGenerator;
import org.openscience.cdk.renderer.generators.IGenerator;
import org.openscience.cdk.renderer.generators.IGeneratorParameter;
import org.openscience.cdk.renderer.visitor.AWTDrawVisitor;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmiFlavor;
import org.openscience.cdk.smiles.SmilesGenerator;
import org.openscience.cdk.smiles.SmilesParser;
import org.openscience.cdk.stereo.TetrahedralChirality;
import org.openscience.cdk.tools.CDKHydrogenAdder;
import org.openscience.cdk.tools.ProteinBuilderTool;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;
import org.openscience.cdk.tools.manipulator.AtomTypeManipulator;
import org.openscience.cdk.tools.manipulator.MolecularFormulaManipulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CDKManipulator extends AbstractChemistryManipulator {

	private static final Logger LOG = LoggerFactory.getLogger(CDKManipulator.class);

	// moderately expensive to create (font embedded) so we have one per
	// manipulator
	private final DepictionGenerator depictionGenerator = new DepictionGenerator();

	/**
	 * removes a extended part of smiles if exists
	 *
	 * @param smiles
	 *            to normalize
	 * @return a normalized smiles
	 */
	private String normalize(String smiles) {
		String result = null;
		String[] components = smiles.split(SMILES_EXTENSION_SEPARATOR_REGEX);
		result = components[0];

		return result;
	}

	/**
	 * replace placeholder "*" with "R" for CDK
	 *
	 * @param extendedSmiles
	 *            extended smiles
	 * @param groups
	 *            a list of RGroups
	 * @return a smiles with RGroups in CDK format
	 */
	private String normalize(String extendedSmiles, List<String> groups) {
		String smiles = null;
		smiles = normalize(extendedSmiles);

		Pattern pattern = Pattern.compile("\\[\\*\\]|\\[\\*:[1-9]\\d*\\]|\\[\\w+:[1-9]\\d*\\]");
		Matcher matcher = pattern.matcher(smiles);
		StringBuilder sb = new StringBuilder();
		int start = 0;
		String rGroup = "";
		int index = 0;
		while (matcher.find() && groups.size() > 0) {
			rGroup = smiles.substring(start, matcher.end());
			rGroup = rGroup.replace(matcher.group(), "[" + groups.get(index) + "]");

			sb.append(rGroup);
			index++;
			start = matcher.end();
		}

		if (start < smiles.length()) {
			sb.append(smiles.substring(start));
		}

		return sb.toString();
	}

	/**
	 *
	 * {@inheritDoc}
	 */
	@Override
	public boolean validateSMILES(String smiles) {
		smiles = normalize(smiles);
		SmilesParser smilesParser = new SmilesParser(SilentChemObjectBuilder.getInstance());
		try {
			IAtomContainer molecule = smilesParser.parseSmiles(smiles);
			if (molecule.getAtomCount() == 0) {
				throw new InvalidSmilesException("invalid smiles!");
			}

		} catch (InvalidSmilesException e) {
			return false;

		}
		return true;
	}

	/**
	 *
	 * {@inheritDoc}
	 */
	@Override
	public MoleculeInfo getMoleculeInfo(AbstractMolecule aMolecule) throws CTKException {
		IAtomContainer molecule = (IAtomContainer) aMolecule.getMolecule();
		MoleculeInfo moleculeInfo = new MoleculeInfo();

		moleculeInfo.setMolecularWeight(AtomContainerManipulator.getNaturalExactMass(molecule));
		moleculeInfo.setMolecularFormula(
				MolecularFormulaManipulator.getString(MolecularFormulaManipulator.getMolecularFormula(molecule)));

		moleculeInfo.setExactMass(MolecularFormulaManipulator
				.getMajorIsotopeMass(MolecularFormulaManipulator.getMolecularFormula(molecule)));

		return moleculeInfo;
	}

	/**
	 * converts smiles to molfile
	 *
	 * @param smiles
	 *            to convert
	 * @return molfile
	 * @throws CTKException
	 */
	private String convertSMILES2MolFile(String smiles) throws CTKException {
		String result = null;

		try (StringWriter stringWriter = new StringWriter();
				MDLV2000Writer writer = new MDLV2000Writer(stringWriter);) {
			try {

				IAtomContainer molecule = getIAtomContainer(smiles);

				writer.writeMolecule(molecule);
				result = stringWriter.toString();
			} catch (InvalidSmilesException e) {
				throw new CTKSmilesException("invalid smiles", e);
			} catch (CDKException e) {
				throw new CTKException("unable to generate coordinates", e);
			} catch (Exception e) {
				throw new CTKException("unable to write molecule", e);
			}
		} catch (IOException e) {
			throw new CTKException("unable to invoke the MDL writer", e);
		}
		return result;

	}

	public String convertMolIntoSmilesWithAtomMapping(String molfile) throws CTKException {
		IAtomContainer molecule = null;
		molecule = getIAtomContainerFromMolFile(molfile);
		SmilesGenerator sg = new SmilesGenerator(SmiFlavor.CxSmiles);
		String smiles;

		try {
			smiles = sg.create(molecule);
			smiles = smiles.replace("*", "[*]");
			return convertExtendedSmiles(smiles);
		} catch (CDKException e) {
			throw new CTKException("unable to create smiles for the given molfile");
		}

	}

	/**
	 * converts molfile to smiles
	 *
	 * @param molfile
	 *            to convert
	 * @return smiles
	 * @throws CTKException
	 */
	private String convertMolFile2SMILES(String molfile) throws CTKException {

		return convertMolecule(new CDKMolecule(getIAtomContainerFromMolFile(molfile)), StType.SMILES);
	}

	private IAtomContainer getIAtomContainerFromMolFile(String molfile) throws CTKException {
		IAtomContainer result = null;

		try (StringReader stringReader = new StringReader(molfile);
				MDLV2000Reader reader = new MDLV2000Reader(stringReader)) {

			IAtomContainer molecule = reader
					.read(SilentChemObjectBuilder.getInstance().newInstance(IAtomContainer.class));
			ElectronDonation model = ElectronDonation.cdk();
			CycleFinder cycles = Cycles.cdkAromaticSet();
			Aromaticity aromaticity = new Aromaticity(model, cycles);

			AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(molecule);
			aromaticity.apply(molecule);
			for (IAtom atom : molecule.atoms()) {
				if (atom instanceof IPseudoAtom) {

					atom.setSymbol("R");
				}

			}
			result = molecule;

		} catch (IllegalArgumentException e) {
			throw new CTKException("illegal argument", e);
		} catch (CDKException e) {
			throw new CTKException("Unable to get a molecule from molfile", e);
		} catch (IOException e) {
			throw new CTKException("unable to invoke the MDL writers/readers", e);

		}

		return result;
	}

	/**
	 *
	 * {@inheritDoc}
	 */
	@Override
	public String convert(String data, StType type) throws CTKException {
		String result = null;

		switch (type) {
		case SMILES:
			result = convertSMILES2MolFile(data);
			break;
		case MOLFILE:
			result = convertMolFile2SMILES(data);
			break;
		case SEQUENCE:
			result = convertMolFile2SMILES(molecule2Smiles(getPolymer(data)));
			break;
		default:
			break;
		}

		return result;
	}

	/**
	 *
	 * {@inheritDoc}
	 */
	@Override
	public String canonicalize(String smiles) throws CTKException, CTKSmilesException {
		IAtomContainer molecule = getIAtomContainer(smiles);
		SmilesGenerator generator = SmilesGenerator.unique();
		String result = null;
		try {
			result = generator.create(molecule);
		} catch (CDKException e) {
			throw new CTKSmilesException("invalid smiles", e);
		}
		return result;
	}

	/**
	 *
	 * {@inheritDoc}
	 */
	@Override
	public byte[] renderMol(String molFile, OutputType outputType, int width, int height, int rgb) throws CTKException {

		DepictionGenerator myGenerator = depictionGenerator.withBackgroundColor(new Color(rgb)).withSize(width, height);
		// can call set 'setZoom(x)' to change the scaling if required, by
		// default depictions are smaller
		// than old version but may still want a setZoom(0.8) etc

		byte[] result;
		IChemObjectBuilder bldr = SilentChemObjectBuilder.getInstance();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		try (StringReader stringReader = new StringReader(molFile);
				MDLV2000Reader molfileReader = new MDLV2000Reader(stringReader)) {

			IAtomContainer mol = molfileReader.read(bldr.newInstance(IAtomContainer.class, 0, 0, 0, 0));
			BufferedImage img = myGenerator.depict(mol).toImg();

			ImageIO.write(img, outputType.toString(), baos);
		} catch (IOException e) {
			throw new CTKException("unable to invoke the reader", e);
		} catch (CDKException e) {
			throw new CTKException("invalid molfile", e);
		}

		result = baos.toByteArray();
		return result;

	}

	/**
	 *
	 * {@inheritDoc}
	 */
	@Override
	public byte[] renderSequence(String sequence, OutputType outputType, int width, int height, int rgb)
			throws CTKException {
		String molFile;
		IAtomContainer molecule = getPolymer(sequence);

		molFile = convertSMILES2MolFile(molecule2Smiles(molecule));

		return renderMol(molFile, outputType, width, height, rgb);

	}

	/**
	 * returns a smiles string represents a given molecule
	 *
	 * @param molecule
	 * @return smiles
	 * @throws CTKException
	 */
	private String molecule2Smiles(IAtomContainer molecule) throws CTKException {
		String result = null;

		SmilesGenerator generator = SmilesGenerator.isomeric();
		try {
			result = generator.create(molecule);
		} catch (CDKException e) {
			throw new CTKException(e.getMessage(), e);
		}

		return result;
	}

	/**
	 * returns a polymer instance of {@link IAtomContainer}
	 *
	 * @param sequence
	 * @return a polymer
	 * @throws CTKException
	 */
	private IAtomContainer getPolymer(String sequence) throws CTKException {
		IAtomContainer polymer;
		try {
			polymer = ProteinBuilderTool.createProtein(sequence, SilentChemObjectBuilder.getInstance());
			CDKAtomTypeMatcher matcher = CDKAtomTypeMatcher.getInstance(polymer.getBuilder());
			for (IAtom atom : polymer.atoms()) {
				IAtomType type = matcher.findMatchingAtomType(polymer, atom);
				AtomTypeManipulator.configure(atom, type);
			}
			CDKHydrogenAdder hydrogenAdder = CDKHydrogenAdder.getInstance(polymer.getBuilder());
			hydrogenAdder.addImplicitHydrogens(polymer);

		} catch (CDKException e) {
			throw new CTKException(e.getMessage(), e);
		}

		return polymer;

	}

	/**
	 *
	 * {@inheritDoc}
	 */
	@Override
	public AbstractMolecule getMolecule(String data, AttachmentList attachments) throws CTKException {
		IAtomContainer molecule = null;

		if (validateSMILES(data))
			molecule = getIAtomContainer(data);
		else
			molecule = getIAtomContainerFromMolFile(data);

		CDKMolecule result = new CDKMolecule(molecule, attachments);

		return result;
	}

	/**
	 * parses smiles to a molecule
	 *
	 * @param smiles
	 *            to parse
	 * @return a molecule instance of {@link IAtomContainer}
	 * @throws CTKException
	 */
	private IAtomContainer getIAtomContainer(String smiles) throws CTKException {
		IAtomContainer molecule = null;
		smiles = normalize(smiles, getRGroupsFromExtendedSmiles(smiles));
		LOG.debug("smiles= " + smiles);
		SmilesParser smilesParser = new SmilesParser(SilentChemObjectBuilder.getInstance());

		try {
			if (smiles.contains(".")) {
				throw new CTKException(
						"Molecule not connected. Use ConnectivityChecker.partitionIntoMolecules() and do the layout for every single component");
			}
			molecule = smilesParser.parseSmiles(smiles);
			StructureDiagramGenerator sdg = new StructureDiagramGenerator();
			sdg.setMolecule(molecule);
			sdg.generateCoordinates();
			molecule = sdg.getMolecule();

			for (IAtom atom : molecule.atoms()) {
				if (atom instanceof IPseudoAtom)
					atom.setSymbol("R");

			}

		} catch (CDKException e) {
			throw new CTKException(e.getMessage(), e);
		}
		return molecule;
	}

	@Override
	protected IBondBase bindAtoms(IAtomBase atom1, IAtomBase atom2) throws CTKException {
		IBondBase bond = null;
		if ((atom1 instanceof CDKAtom) && (atom1 instanceof CDKAtom)) {

			IBond newBond = new Bond(((CDKAtom) atom1).getMolAtom(), ((CDKAtom) atom2).getMolAtom());

			bond = new CDKBond(newBond);

		} else {
			throw new CTKException("invalid atoms");
		}

		return bond;
	}

	/**
	 * @param molecule
	 *            given molecule
	 * @param rGroup
	 *            given rgroup
	 * @param atom1
	 *            given atom1
	 * @param atom2
	 *            given atom2
	 * @return IStereoElementBase
	 */

	@Override
	protected IStereoElementBase getStereoInformation(AbstractMolecule molecule, IAtomBase rGroup, IAtomBase atom1,
			IAtomBase atom2) {
		IStereoElement elementToAdd = null;
		IBond bondToAdd = null;
		for (IStereoElement element : (((CDKMolecule) molecule).getMolecule().stereoElements())) {
			if (element.contains(((CDKAtom) rGroup).getMolAtom())) {
				if (element instanceof ITetrahedralChirality) {
					IAtom[] atomArray = ((ITetrahedralChirality) element).getLigands();
					for (int i = 0; i < atomArray.length; i++) {
						if (atomArray[i].equals(((CDKAtom) rGroup).getMolAtom())) {

							Stereo st = null;
							try {
								st = ((CDKBond) rGroup.getIBond(0)).bond.getStereo();
							} catch (CTKException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							bondToAdd = new Bond((IAtom) atom1.getMolAtom(), (IAtom) atom2.getMolAtom());
							bondToAdd.setStereo(st);

							atomArray[i] = (IAtom) atom1.getMolAtom();

							break;
						}

					}

					elementToAdd = new TetrahedralChirality(((ITetrahedralChirality) element).getChiralAtom(),
							atomArray, (((ITetrahedralChirality) element).getStereo()));

				}
			}
		}
		CDKStereoElement stereo = new CDKStereoElement(elementToAdd);
		stereo.setBond(bondToAdd);
		return stereo;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @throws CTKException
	 *             general ChemToolKit exception passed to HELMToolKit
	 */
	@Override
	public String convertMolecule(AbstractMolecule container, StType type) throws CTKException {
		String result = null;

		IAtomContainer molecule = (IAtomContainer) container.getMolecule();

		switch (type) {
		case SMILES:
			result = molecule2Smiles(molecule);
			break;
		case MOLFILE:
			result = molecule2Molfile(molecule);
		default:
			break;
		}
		return result;
	}

	/**
	 * @param molecule
	 *            given molecule
	 * @return molfile of the molcule
	 * @throws CTKException
	 *             general ChemToolKit exception passed to HELMToolKit
	 */
	private String molecule2Molfile(IAtomContainer molecule) throws CTKException {
		String result = null;
		try (StringWriter stringWriter = new StringWriter(); MDLV2000Writer writer = new MDLV2000Writer(stringWriter)) {

			writer.writeMolecule(molecule);
			result = stringWriter.toString();
		} catch (CDKException e) {
			throw new CTKException("unable to generate coordinates", e);
		} catch (Exception e) {
			throw new CTKException("unable to write molecule", e);

		}
		return result;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected boolean setStereoInformation(AbstractMolecule firstContainer, IAtomBase firstRgroup,
			AbstractMolecule secondContainer, IAtomBase secondRgroup, IAtomBase atom1, IAtomBase atom2)
					throws CTKException {
		boolean isStereo = super.setStereoInformation(firstContainer, firstRgroup, secondContainer, secondRgroup, atom1,
				atom2);
		if (!isStereo)
			firstContainer.addIBase(bindAtoms(atom1, atom2));
		return isStereo;
	}

}
