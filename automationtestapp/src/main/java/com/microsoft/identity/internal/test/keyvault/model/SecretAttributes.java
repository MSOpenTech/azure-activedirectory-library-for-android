/*
 * KeyVaultClient
 * The key vault client performs cryptographic key operations and vault operations against the Key Vault service.
 *
 * OpenAPI spec version: 2016-10-01
 * 
 *
 * NOTE: This class is auto generated by the swagger code generator program.
 * https://github.com/swagger-api/swagger-codegen.git
 * Do not edit the class manually.
 */


package com.microsoft.identity.internal.test.keyvault.model;

import java.util.Objects;

import com.google.gson.annotations.SerializedName;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * The secret management attributes.
 */
@ApiModel(description = "The secret management attributes.")

public class SecretAttributes {
  @SerializedName("enabled")
  private Boolean enabled = null;

  @SerializedName("nbf")
  private Integer nbf = null;

  @SerializedName("exp")
  private Integer exp = null;

  @SerializedName("created")
  private Integer created = null;

  @SerializedName("updated")
  private Integer updated = null;

  public SecretAttributes enabled(Boolean enabled) {
    this.enabled = enabled;
    return this;
  }

   /**
   * Determines whether the object is enabled.
   * @return enabled
  **/
  @ApiModelProperty(value = "Determines whether the object is enabled.")
  public Boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public SecretAttributes nbf(Integer nbf) {
    this.nbf = nbf;
    return this;
  }

   /**
   * Not before date in UTC.
   * @return nbf
  **/
  @ApiModelProperty(value = "Not before date in UTC.")
  public Integer getNbf() {
    return nbf;
  }

  public void setNbf(Integer nbf) {
    this.nbf = nbf;
  }

  public SecretAttributes exp(Integer exp) {
    this.exp = exp;
    return this;
  }

   /**
   * Expiry date in UTC.
   * @return exp
  **/
  @ApiModelProperty(value = "Expiry date in UTC.")
  public Integer getExp() {
    return exp;
  }

  public void setExp(Integer exp) {
    this.exp = exp;
  }

   /**
   * Creation time in UTC.
   * @return created
  **/
  @ApiModelProperty(value = "Creation time in UTC.")
  public Integer getCreated() {
    return created;
  }

   /**
   * Last updated time in UTC.
   * @return updated
  **/
  @ApiModelProperty(value = "Last updated time in UTC.")
  public Integer getUpdated() {
    return updated;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SecretAttributes secretAttributes = (SecretAttributes) o;
    return Objects.equals(this.enabled, secretAttributes.enabled) &&
        Objects.equals(this.nbf, secretAttributes.nbf) &&
        Objects.equals(this.exp, secretAttributes.exp) &&
        Objects.equals(this.created, secretAttributes.created) &&
        Objects.equals(this.updated, secretAttributes.updated);
  }

  @Override
  public int hashCode() {
    return Objects.hash(enabled, nbf, exp, created, updated);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class SecretAttributes {\n");
    
    sb.append("    enabled: ").append(toIndentedString(enabled)).append("\n");
    sb.append("    nbf: ").append(toIndentedString(nbf)).append("\n");
    sb.append("    exp: ").append(toIndentedString(exp)).append("\n");
    sb.append("    created: ").append(toIndentedString(created)).append("\n");
    sb.append("    updated: ").append(toIndentedString(updated)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(java.lang.Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }

}

