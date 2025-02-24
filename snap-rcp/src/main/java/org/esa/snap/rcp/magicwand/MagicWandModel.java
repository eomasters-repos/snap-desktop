/*
 * Copyright (C) 2012 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.snap.rcp.magicwand;

import com.bc.ceres.core.Assert;
import com.bc.ceres.multilevel.MultiLevelImage;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.SingleValueConverter;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Mask;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.dataop.barithm.BandArithmetic;
import org.esa.snap.core.dataop.barithm.RasterDataSymbol;
import org.esa.snap.core.gpf.common.resample.Resample;
import org.esa.snap.core.jexp.ParseException;
import org.esa.snap.core.jexp.Term;
import org.esa.snap.core.util.ObjectUtils;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.core.util.StringUtils;

import javax.media.jai.Interpolation;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Utilities for the magic wand tool (interactor).
 *
 * @author Norman Fomferra
 * @since BEAM 4.10
 */
public class MagicWandModel implements Cloneable {

    public enum PickMode {
        SINGLE,
        PLUS,
        MINUS,
    }

    public enum SpectrumTransform {
        INTEGRAL,
        IDENTITY,
        DERIVATIVE,
    }

    public enum PixelTest {
        DISTANCE,
        AVERAGE,
        LIMITS,
    }

    public static final String MAGIC_WAND_MASK_NAME = "magic_wand";

    private double tolerance;
    private double minTolerance;
    private double maxTolerance;
    private ArrayList<String> bandNames;
    private SpectrumTransform spectrumTransform;
    private PixelTest pixelTest;
    private boolean normalize;
    private PickMode pickMode;
    private ArrayList<double[]> plusSpectra;
    private ArrayList<double[]> minusSpectra;

    private transient ArrayList<Listener> listeners;

    public MagicWandModel() {
        pickMode = PickMode.SINGLE;
        pixelTest = PixelTest.DISTANCE;
        spectrumTransform = SpectrumTransform.IDENTITY;
        bandNames = new ArrayList<>();
        plusSpectra = new ArrayList<>();
        minusSpectra = new ArrayList<>();
        tolerance = 0.1;
        minTolerance = 0.0;
        maxTolerance = 1.0;
    }

    public void addListener(Listener listener) {
        if (listeners == null) {
            listeners = new ArrayList<>();
        }
        listeners.add(listener);
    }

    public void removeListeners() {
        if (listeners != null) {
            listeners.clear();
            listeners = null;
        }
    }

    public void fireModelChanged(boolean recomputeMask) {
        if (listeners != null) {
            for (Listener listener : listeners) {
                listener.modelChanged(this, recomputeMask);
            }
        }
    }

    @SuppressWarnings("CloneDoesntDeclareCloneNotSupportedException")
    @Override
    public MagicWandModel clone() {
        try {
            MagicWandModel clone = (MagicWandModel) super.clone();
            clone.bandNames = new ArrayList<>(bandNames);
            clone.plusSpectra = new ArrayList<>(plusSpectra);
            clone.minusSpectra = new ArrayList<>(minusSpectra);
            clone.listeners = null;
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
    }

    public void assign(MagicWandModel other) {
        pixelTest = other.pixelTest;
        spectrumTransform = other.spectrumTransform;
        pickMode = other.pickMode;
        tolerance = other.tolerance;
        minTolerance = other.minTolerance;
        maxTolerance = other.maxTolerance;
        bandNames = new ArrayList<>(other.bandNames);
        plusSpectra = new ArrayList<>(other.plusSpectra);
        minusSpectra = new ArrayList<>(other.minusSpectra);
        fireModelChanged(true);
    }

    int getSpectrumCount() {
        return plusSpectra.size() + minusSpectra.size();
    }

    int getPlusSpectrumCount() {
        return plusSpectra.size();
    }

    int getMinusSpectrumCount() {
        return minusSpectra.size();
    }

    int getBandCount() {
        return bandNames.size();
    }

    void addSpectrum(double... spectrum) {
        Assert.argument(spectrum.length == bandNames.size(), "spectrum size does not match # selected bands");
        if (pickMode == PickMode.SINGLE) {
            plusSpectra.clear();
            minusSpectra.clear();
            plusSpectra.add(spectrum);
        } else if (pickMode == PickMode.PLUS) {
            plusSpectra.add(spectrum);
        } else if (pickMode == PickMode.MINUS) {
            minusSpectra.add(spectrum);
        }
        fireModelChanged(true);
    }

    void clearSpectra() {
        plusSpectra.clear();
        minusSpectra.clear();
        fireModelChanged(true);
    }

    public List<String> getBandNames() {
        return Collections.unmodifiableList(bandNames);
    }

    public void setBandNames(String... bandNames) {
        setBandNames(Arrays.asList(bandNames));
    }

    public void setBandNames(List<String> bandNames) {
        plusSpectra.clear();
        minusSpectra.clear();
        this.bandNames = new ArrayList<>(bandNames);
        fireModelChanged(true);
    }

    public PickMode getPickMode() {
        return pickMode;
    }

    public void setPickMode(PickMode pickMode) {
        this.pickMode = pickMode;
        fireModelChanged(false);
    }

    public PixelTest getPixelTest() {
        return pixelTest;
    }

    public void setPixelTest(PixelTest pixelTest) {
        this.pixelTest = pixelTest;
        fireModelChanged(false);
    }

    public SpectrumTransform getSpectrumTransform() {
        return spectrumTransform;
    }

    public void setSpectrumTransform(SpectrumTransform spectrumTransform) {
        this.spectrumTransform = spectrumTransform;
        fireModelChanged(false);
    }

    public double getTolerance() {
        return tolerance;
    }

    public void setTolerance(double tolerance) {
        this.tolerance = tolerance;
        fireModelChanged(true);
    }

    public double getMinTolerance() {
        return minTolerance;
    }

    public void setMinTolerance(double minTolerance) {
        this.minTolerance = minTolerance;
        fireModelChanged(false);
    }

    public double getMaxTolerance() {
        return maxTolerance;
    }

    public void setMaxTolerance(double maxTolerance) {
        this.maxTolerance = maxTolerance;
        fireModelChanged(false);
    }

    public void setNormalize(boolean normalize) {
        this.normalize = normalize;
        fireModelChanged(false);
    }

    public void setSpectralBandNames(Product product) {
        List<Band> bands = getSpectralBands(product);
        List<String> bandNames = new ArrayList<>();
        for (Band band : bands) {
            bandNames.add(band.getName());
        }
        if (!bandNames.isEmpty()) {
            setBandNames(bandNames.toArray(new String[bandNames.size()]));
        }
    }

    public static List<Band> getSpectralBands(Product product) {
        List<Band> bands = new ArrayList<>();
        for (Band band : product.getBands()) {
            if (band.getSpectralWavelength() > 0.0 || band.getSpectralBandIndex() >= 0) {
                bands.add(band);
            }
        }
        Collections.sort(bands, new SpectralBandComparator());
        return bands;
    }


    List<Band> getBands(Product product) {
        List<String> names = getBandNames();
        List<Band> bands = new ArrayList<>(names.size());
        for (String name : names) {
            Band band = product.getBand(name);
            if (band == null) {
                return null;
            }
            bands.add(band);
        }
        return bands;
    }

    static void setMagicWandMask(Product product, String expression) {

        String validMaskExpression;
        try {
            validMaskExpression = BandArithmetic.getValidMaskExpression(expression, product, null);
        } catch (ParseException e) {
            validMaskExpression = null;
        }

        if (validMaskExpression != null) {
            expression = "(" + validMaskExpression + ") && (" + expression + ")";
        }

        // Current mask
        final Mask magicWandMask = product.getMaskGroup().get(MAGIC_WAND_MASK_NAME);
        if (magicWandMask != null) { // The magic wand mask exists.

            // One must check the rasters size defined in the expression vs the current magic wand mask (in case of multi-size product).
            try {
                // Decipher the expression to get the rasters. At this stage we know the rasters have the same size.
                Term term = BandArithmetic.parseExpression(expression, product);
                RasterDataSymbol[] rasterDS = BandArithmetic.getRefRasterDataSymbols(term);
                if (rasterDS.length > 0) {
                    int rastersWidth = rasterDS[0].getRaster().getRasterWidth();
                    int rastersHeight = rasterDS[0].getRaster().getRasterHeight();

                    // Current mask = same resolution as bands in expression
                    if (magicWandMask.getRasterWidth() == rastersWidth &&
                        magicWandMask.getRasterHeight() == rastersHeight){

                        // set the new expression to the magicwand mask
                        magicWandMask.getImageConfig().setValue("expression", expression);

                        // TODO compute mask with other resolutions than magicWandMask by resampling

                        //test
                        String mask20Name = MAGIC_WAND_MASK_NAME + "_20";
                        Mask mask20 = product.getMaskGroup().get(mask20Name);
                        if (mask20 != null) product.getMaskGroup().remove(mask20);

                        MultiLevelImage multiLevel20m = Resample.createInterpolatedMultiLevelImage(magicWandMask.getSourceImage(),
                                magicWandMask.getNoDataValue(), magicWandMask.getImageToModelTransform(),
                                5490,5490, new Dimension(512,512),
                                product.getBand("B5").getMultiLevelModel(), Interpolation.getInstance(Interpolation.INTERP_NEAREST));

                        mask20 = new Mask(mask20Name,5490,5490, magicWandMask.getImageType());
                        ProductUtils.copyGeoCoding(product.getBand("B5"), mask20);
                        mask20.setSourceImage(multiLevel20m);
                        product.addMask(mask20);


                    } else { // Resolution of bands in expression different from current mask

//                        // To avoid false alarm, as at creation mask has product resolution
//                        if (!magicWandMask.getImageConfig().getProperty("expression").getValue().equals("0")) {
//                            MultiSizeIssue.warningMaskForBandsWithDifferentSize();
//                        }

                        // Delete the current magicWandMask (not the same resolution as bands defined in the expression)
                        product.getMaskGroup().remove(magicWandMask);

                        // Create the mask with the current expression
                        Mask mask = product.addMask(MAGIC_WAND_MASK_NAME,
                                        expression, "Magic wand mask",
                                        Color.RED, 0.5);


                        // TODO must compute masks with other resolutions than the new magicWandMask by resampling
//                        //test
//                        MultiLevelImage multiLevel10m = Resample.createInterpolatedMultiLevelImage(mask.getSourceImage(),mask.getNoDataValue(),mask.getImageToModelTransform(),
//                                10980,10980,new Dimension(512,512),product.getBand("B2").getMultiLevelModel(), Interpolation.getInstance(Interpolation.INTERP_NEAREST));
//                        Mask mask10 = new Mask("test10",10980,10980,mask.getImageType());
//                        mask10.setSourceImage(multiLevel10m);
//                        ProductUtils.copyGeoCoding(product.getBand("B2"),mask10);

//                        scaleMultiLevelImage(MultiLevelImage masterImage, MultiLevelImage sourceImage,
//                        float[] scalings, GeneralFilterFunction filterFunction, RenderingHints renderingHints,
//                        double noDataValue, Interpolation interpolation)

                        // Set the expression to the new magic wand mask
                        Mask newMagicWandMask = product.getMaskGroup().get(MAGIC_WAND_MASK_NAME);
                        newMagicWandMask.getImageConfig().setValue("expression", expression);

                    }
                } // if the expression defines no valid raster: do nothing

            } catch (ParseException pe) {
                final String msg = String.format("Expression '%s' is invalid.\n", expression);
                throw new IllegalArgumentException(msg, pe);
            }

        } else {

            // First time : create magic_wand mask with product resolution by default
            Mask mask = product.addMask(MAGIC_WAND_MASK_NAME,
                            expression, "Magic wand mask",
                            Color.RED, 0.5);
//            //test
//            MultiLevelImage multiLevel10m = Resample.createInterpolatedMultiLevelImage(mask.getSourceImage(),mask.getNoDataValue(),mask.getImageToModelTransform(),
//                    10980,10980,new Dimension(512,512),product.getBand("B2").getMultiLevelModel(), Interpolation.getInstance(Interpolation.INTERP_NEAREST));
//            Mask mask10 = new Mask(MAGIC_WAND_MASK_NAME + "_10",10980,10980,mask.getImageType());
//            ProductUtils.copyGeoCoding(product.getBand("B2"),mask10);
//            mask10.setSourceImage(multiLevel10m);
//            product.addMask(mask10);
        }
    }

    String createMaskExpression() {
        final String plusPart;
        final String minusPart;
        if (getPixelTest() == PixelTest.DISTANCE) {
            plusPart = getDistancePart(bandNames, spectrumTransform, plusSpectra, tolerance, normalize);
            minusPart = getDistancePart(bandNames, spectrumTransform, minusSpectra, tolerance, normalize);
        } else if (getPixelTest() == PixelTest.AVERAGE) {
            plusPart = getAveragePart(bandNames, spectrumTransform, plusSpectra, tolerance, normalize);
            minusPart = getAveragePart(bandNames, spectrumTransform, minusSpectra, tolerance, normalize);
        } else if (getPixelTest() == PixelTest.LIMITS) {
            plusPart = getLimitsPart(bandNames, spectrumTransform, plusSpectra, tolerance, normalize);
            minusPart = getLimitsPart(bandNames, spectrumTransform, minusSpectra, tolerance, normalize);
        } else {
            throw new IllegalStateException("Unhandled method " + getPixelTest());
        }
        if (plusPart != null && minusPart != null) {
            return String.format("(%s) && !(%s)", plusPart, minusPart);
        } else if (plusPart != null) {
            return plusPart;
        } else if (minusPart != null) {
            return String.format("!(%s)", minusPart);
        } else {
            return "0";
        }
    }

    private static String getDistancePart(List<String> bandNames, SpectrumTransform spectrumTransform, List<double[]> spectra, double tolerance, boolean normalize) {
        if (spectra.isEmpty()) {
            return null;
        }
        final StringBuilder part = new StringBuilder();
        for (int i = 0; i < spectra.size(); i++) {
            double[] spectrum = getSpectrum(spectra.get(i), normalize);
            if (i > 0) {
                part.append(" || ");
            }
            part.append(getDistanceSubPart(bandNames, spectrumTransform, spectrum, tolerance, normalize));
        }
        return part.toString();
    }

    private static String getAveragePart(List<String> bandNames, SpectrumTransform spectrumTransform, List<double[]> spectra, double tolerance, boolean normalize) {
        if (spectra.isEmpty()) {
            return null;
        }
        double[] avgSpectrum = getAvgSpectrum(bandNames.size(), spectra, normalize);
        return getDistanceSubPart(bandNames, spectrumTransform, avgSpectrum, tolerance, normalize);
    }

    private static String getLimitsPart(List<String> bandNames, SpectrumTransform spectrumTransform, List<double[]> spectra, double tolerance, boolean normalize) {
        if (spectra.isEmpty()) {
            return null;
        }
        double[] minSpectrum = getMinSpectrum(bandNames.size(), spectra, tolerance, normalize);
        double[] maxSpectrum = getMaxSpectrum(bandNames.size(), spectra, tolerance, normalize);
        return getLimitsSubPart(bandNames, spectrumTransform, minSpectrum, maxSpectrum, normalize);
    }

    private static double[] getSpectrum(double[] spectrum, boolean normalize) {
        if (normalize) {
            double[] normSpectrum = new double[spectrum.length];
            for (int i = 0; i < spectrum.length; i++) {
                normSpectrum[i] = getSpectrumValue(spectrum, i, true);
            }
            return normSpectrum;
        } else {
            return spectrum;
        }
    }

    private static double[] getAvgSpectrum(int numBands, List<double[]> spectra, boolean normalize) {
        double[] avgSpectrum = new double[numBands];
        for (double[] spectrum : spectra) {
            for (int i = 0; i < spectrum.length; i++) {
                double value = getSpectrumValue(spectrum, i, normalize);
                avgSpectrum[i] += value;
            }
        }
        for (int i = 0; i < avgSpectrum.length; i++) {
            avgSpectrum[i] /= spectra.size();
        }
        return avgSpectrum;
    }

    private static double[] getMinSpectrum(int numBands, List<double[]> spectra, double tolerance, boolean normalize) {
        double[] minSpectrum = new double[numBands];
        Arrays.fill(minSpectrum, +Double.MAX_VALUE);
        for (double[] spectrum : spectra) {
            for (int i = 0; i < spectrum.length; i++) {
                double value = getSpectrumValue(spectrum, i, normalize);
                minSpectrum[i] = Math.min(minSpectrum[i], value - tolerance);
            }
        }
        return minSpectrum;
    }

    private static double[] getMaxSpectrum(int numBands, List<double[]> spectra, double tolerance, boolean normalize) {
        double[] maxSpectrum = new double[numBands];
        Arrays.fill(maxSpectrum, -Double.MAX_VALUE);
        for (double[] spectrum : spectra) {
            for (int i = 0; i < spectrum.length; i++) {
                double value = getSpectrumValue(spectrum, i, normalize);
                maxSpectrum[i] = Math.max(maxSpectrum[i], value + tolerance);
            }
        }
        return maxSpectrum;
    }

    private static double getSpectrumValue(double[] spectrum, int i, boolean normalize) {
        return normalize ? spectrum[i] / spectrum[0] : spectrum[i];
    }

    private static String getDistanceSubPart(List<String> bandNames, SpectrumTransform spectrumTransform, double[] spectrum, double tolerance, boolean normalize) {
        if (bandNames.isEmpty()) {
            return "0";
        }
        final StringBuilder arguments = new StringBuilder();
        appendSpectrumBandNames(bandNames, normalize, arguments);
        appendSpectrumBandValues(spectrum, arguments);
        String functionName;
        if (spectrumTransform == SpectrumTransform.IDENTITY) {
            functionName = "distance";
        } else if (spectrumTransform == SpectrumTransform.DERIVATIVE) {
            functionName = "distance_deriv";
        } else if (spectrumTransform == SpectrumTransform.INTEGRAL) {
            functionName = "distance_integ";
        } else {
            throw new IllegalStateException("unhandled operator " + spectrumTransform);
        }
        if (bandNames.size() == 1) {
            return String.format("%s(%s) < %s", functionName, arguments, tolerance);
        } else {
            return String.format("%s(%s)/%s < %s", functionName, arguments, bandNames.size(), tolerance);
        }
    }

    private static String getLimitsSubPart(List<String> bandNames, SpectrumTransform spectrumTransform, double[] minSpectrum, double[] maxSpectrum, boolean normalize) {
        if (bandNames.isEmpty()) {
            return "0";
        }
        final StringBuilder arguments = new StringBuilder();
        appendSpectrumBandNames(bandNames, normalize, arguments);
        appendSpectrumBandValues(minSpectrum, arguments);
        appendSpectrumBandValues(maxSpectrum, arguments);
        String functionName;
        if (spectrumTransform == SpectrumTransform.IDENTITY) {
            functionName = "inrange";
        } else if (spectrumTransform == SpectrumTransform.DERIVATIVE) {
            functionName = "inrange_deriv";
        } else if (spectrumTransform == SpectrumTransform.INTEGRAL) {
            functionName = "inrange_integ";
        } else {
            throw new IllegalStateException("unhandled operator " + spectrumTransform);
        }
        return String.format("%s(%s)", functionName, arguments);
    }

    private static void appendSpectrumBandNames(List<String> bandNames, boolean normalize, StringBuilder arguments) {
        String firstName = BandArithmetic.createExternalName(bandNames.get(0));
        for (int i = 0; i < bandNames.size(); i++) {
            if (i > 0) {
                arguments.append(",");
            }
            String name = BandArithmetic.createExternalName(bandNames.get(i));
            if (normalize) {
                arguments.append(name).append("/").append(firstName);
            } else {
                arguments.append(name);
            }
        }
    }

    private static void appendSpectrumBandValues(double[] spectrum, StringBuilder arguments) {
        for (double value : spectrum) {
            arguments.append(",");
            arguments.append(value);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MagicWandModel that = (MagicWandModel) o;

        if (Double.compare(that.maxTolerance, maxTolerance) != 0) return false;
        if (Double.compare(that.minTolerance, minTolerance) != 0) return false;
        if (normalize != that.normalize) return false;
        if (Double.compare(that.tolerance, tolerance) != 0) return false;
        if (pixelTest != that.pixelTest) return false;
        if (pickMode != that.pickMode) return false;
        if (spectrumTransform != that.spectrumTransform) return false;
        if (!ObjectUtils.equalObjects(bandNames, that.bandNames)) return false;
        if (!ObjectUtils.equalObjects(plusSpectra.toArray(), that.plusSpectra.toArray())) return false;
        if (!ObjectUtils.equalObjects(minusSpectra.toArray(), that.minusSpectra.toArray())) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = pickMode.hashCode();
        result = 31 * result + spectrumTransform.hashCode();
        result = 31 * result + pixelTest.hashCode();
        result = 31 * result + plusSpectra.hashCode();
        result = 31 * result + minusSpectra.hashCode();
        result = 31 * result + bandNames.hashCode();
        temp = tolerance != +0.0d ? Double.doubleToLongBits(tolerance) : 0L;
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (normalize ? 1 : 0);
        temp = minTolerance != +0.0d ? Double.doubleToLongBits(minTolerance) : 0L;
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = maxTolerance != +0.0d ? Double.doubleToLongBits(maxTolerance) : 0L;
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    public static MagicWandModel fromXml(String xml) {
        return (MagicWandModel) createXStream().fromXML(xml);
    }

    public String toXml() {
        return createXStream().toXML(this);
    }

    private static XStream createXStream() {
        final XStream xStream = new XStream();
        xStream.alias("magicWandSettings", MagicWandModel.class);
        xStream.registerConverter(new SingleValueConverter() {
            @Override
            public boolean canConvert(Class type) {
                return type.equals(double[].class);
            }

            @Override
            public String toString(Object obj) {
                return StringUtils.arrayToString(obj, ",");
            }

            @Override
            public Object fromString(String str) {
                return StringUtils.toDoubleArray(str, ",");
            }
        });
        return xStream;
    }

    public static interface Listener {
        void modelChanged(MagicWandModel model, boolean recomputeMask);
    }
}
