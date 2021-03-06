//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, vJAXB 2.1.10 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2010.08.25 at 03:25:44 PM EDT 
//


package org.voltdb.benchmark.workloads.xml;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for loaderType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="loaderType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="loaderClass" maxOccurs="unbounded">
 *           &lt;complexType>
 *             &lt;complexContent>
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *                 &lt;attribute name="pathName" type="{http://www.w3.org/2001/XMLSchema}anyURI" />
 *               &lt;/restriction>
 *             &lt;/complexContent>
 *           &lt;/complexType>
 *         &lt;/element>
 *       &lt;/sequence>
 *       &lt;attribute name="loaderName" type="{http://www.w3.org/2001/XMLSchema}string" />
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "loaderType", propOrder = {
    "loaderClass"
})
public class LoaderType {

    @XmlElement(required = true)
    protected List<LoaderType.LoaderClass> loaderClass;
    @XmlAttribute
    protected String loaderName;

    /**
     * Gets the value of the loaderClass property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the loaderClass property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getLoaderClass().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link LoaderType.LoaderClass }
     * 
     * 
     */
    public List<LoaderType.LoaderClass> getLoaderClass() {
        if (loaderClass == null) {
            loaderClass = new ArrayList<LoaderType.LoaderClass>();
        }
        return this.loaderClass;
    }

    /**
     * Gets the value of the loaderName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getLoaderName() {
        return loaderName;
    }

    /**
     * Sets the value of the loaderName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setLoaderName(String value) {
        this.loaderName = value;
    }


    /**
     * <p>Java class for anonymous complex type.
     * 
     * <p>The following schema fragment specifies the expected content contained within this class.
     * 
     * <pre>
     * &lt;complexType>
     *   &lt;complexContent>
     *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
     *       &lt;attribute name="pathName" type="{http://www.w3.org/2001/XMLSchema}anyURI" />
     *     &lt;/restriction>
     *   &lt;/complexContent>
     * &lt;/complexType>
     * </pre>
     * 
     * 
     */
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "")
    public static class LoaderClass {

        @XmlAttribute
        @XmlSchemaType(name = "anyURI")
        protected String pathName;

        /**
         * Gets the value of the pathName property.
         * 
         * @return
         *     possible object is
         *     {@link String }
         *     
         */
        public String getPathName() {
            return pathName;
        }

        /**
         * Sets the value of the pathName property.
         * 
         * @param value
         *     allowed object is
         *     {@link String }
         *     
         */
        public void setPathName(String value) {
            this.pathName = value;
        }

    }

}
