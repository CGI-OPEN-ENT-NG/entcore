/*
 * Copyright © WebServices pour l'Éducation, 2015
 *
 * This file is part of ENT Core. ENT Core is a versatile ENT engine based on the JVM.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with ENT Core is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of ENT Core, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package org.entcore.auth.security;

import fr.wseduc.webutils.data.ZLib;
import org.joda.time.DateTime;
import org.opensaml.DefaultBootstrap;
import org.opensaml.common.SAMLObject;
import org.opensaml.common.SAMLVersion;
import org.opensaml.common.xml.SAMLConstants;
import org.opensaml.saml2.core.*;
import org.opensaml.saml2.core.impl.*;
import org.opensaml.saml2.encryption.Decrypter;
import org.opensaml.saml2.metadata.*;
import org.opensaml.saml2.metadata.provider.FilesystemMetadataProvider;
import org.opensaml.saml2.metadata.provider.MetadataProviderException;
import org.opensaml.security.MetadataCredentialResolver;
import org.opensaml.security.MetadataCriteria;
import org.opensaml.security.SAMLSignatureProfileValidator;
import org.opensaml.xml.Configuration;
import org.opensaml.xml.ConfigurationException;
import org.opensaml.xml.encryption.InlineEncryptedKeyResolver;
import org.opensaml.xml.io.MarshallingException;
import org.opensaml.xml.parse.BasicParserPool;
import org.opensaml.xml.schema.XSString;
import org.opensaml.xml.schema.impl.XSStringBuilder;
import org.opensaml.xml.security.CriteriaSet;
import org.opensaml.xml.security.credential.UsageType;
import org.opensaml.xml.security.criteria.EntityIDCriteria;
import org.opensaml.xml.security.criteria.UsageCriteria;
import org.opensaml.xml.security.keyinfo.KeyInfoCredentialResolver;
import org.opensaml.xml.security.keyinfo.StaticKeyInfoCredentialResolver;
import org.opensaml.xml.security.x509.BasicX509Credential;
import org.opensaml.xml.signature.Signature;
import org.opensaml.xml.signature.SignatureTrustEngine;
import org.opensaml.xml.signature.X509Certificate;
import org.opensaml.xml.signature.impl.ExplicitKeySignatureTrustEngine;
import org.opensaml.xml.util.XMLHelper;
import org.opensaml.xml.validation.ValidationException;
import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.json.impl.Base64;
import org.w3c.dom.Element;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;

import static fr.wseduc.webutils.Utils.isNotEmpty;

public class SamlValidator extends BusModBase implements Handler<Message<JsonObject>> {

	private final Map<String, SignatureTrustEngine> signatureTrustEngineMap = new HashMap<>();
	private final Map<String, EntityDescriptor> entityDescriptorMap = new HashMap<>();
	private RSAPrivateKey privateKey;
	private String issuer;

	@Override
	public void start() {
		super.start();
		try {
			DefaultBootstrap.bootstrap();
			String path = config.getString("saml-metadata-folder");
			if (path == null || path.trim().isEmpty()) {
				logger.error("Metadata folder not found.");
				return;
			}
			issuer = config.getString("saml-issuer");
			if (issuer == null || issuer.trim().isEmpty()) {
				logger.error("Empty issuer");
				return;
			}
			for (String f : vertx.fileSystem().readDirSync(path)) {
				loadSignatureTrustEngine(f);
			}
			loadPrivateKey(config.getString("saml-private-key"));
			vertx.eventBus().registerLocalHandler("saml", this);
		} catch (ConfigurationException | MetadataProviderException | InvalidKeySpecException | NoSuchAlgorithmException e) {
			logger.error("Error loading SamlValidator.", e);
		}
	}

	private void loadPrivateKey(String path) throws NoSuchAlgorithmException, InvalidKeySpecException {
		logger.info("loadPrivateKey : " + path);
		if (path != null && !path.trim().isEmpty() && vertx.fileSystem().existsSync(path)) {
			byte[] encodedPrivateKey = vertx.fileSystem().readFileSync(path).getBytes();
			PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(encodedPrivateKey);
			privateKey = (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(privateKeySpec);
		}
	}

	@Override
	public void handle(Message<JsonObject> message) {
		final String action = message.body().getString("action", "");
		final String response = message.body().getString("response");
		final String idp = message.body().getString("IDP");
		if (!"generate-slo-request".equals(action) &&
				!"generate-authn-request".equals(action) &&
				!"generate-saml-response".equals(action) &&
				(response == null || response.trim().isEmpty())) {
			sendError(message, "invalid.response");
			return;
		}
		try {
			switch (action) {
				case "generate-authn-request" :
					String sp = message.body().getString("SP");
					String acs = message.body().getString("acs");
					boolean sign = message.body().getBoolean("AuthnRequestsSigned", false);
					if (message.body().getBoolean("SimpleSPEntityID", false)) {
						sendOK(message, generateSimpleSPEntityIDRequest(idp, sp));
					} else {
						sendOK(message, generateAuthnRequest(idp, sp, acs, sign));
					}
					break;
				case "generate-saml-response" :
					String serviceProvider = message.body().getString("SP");
					String userId = message.body().getString("userId");
					sendOK(message, generateSAMLResponse(idp, serviceProvider));
					break;
				case "validate-signature":
					sendOK(message, new JsonObject().putBoolean("valid", validateSignature(response)));
					break;
				case "decrypt-assertion":
					sendOK(message, new JsonObject().putString("assertion", decryptAssertion(response)));
					break;
				case "validate-signature-decrypt":
					final JsonObject res = new JsonObject();
					if (validateSignature(response)) {
						res.putBoolean("valid", true).putString("assertion", decryptAssertion(response));
					} else {
						res.putBoolean("valid", false).putString("assertion", null);
					}
					sendOK(message, res);
					break;
				case "generate-slo-request":
					String sessionIndex = message.body().getString("SessionIndex");
					String nameID = message.body().getString("NameID");
					sendOK(message, new JsonObject().putString("slo", generateSloRequest(nameID, sessionIndex, idp)));
					break;
				default:
					sendError(message, "invalid.action");
			}
		} catch (Exception e) {
			sendError(message, e.getMessage(), e);
		}
	}


	public JsonObject generateSAMLResponse(String idp, String serviceProvider)
			throws SignatureException, NoSuchAlgorithmException, InvalidKeyException, UnsupportedEncodingException, MarshallingException {
		logger.info("start generating SAMLResponse");
		logger.info("IDP : " + idp);
		logger.info("SP : " + serviceProvider);

		// --- TAG Issuer ---
		Issuer issuer = createIssuer(serviceProvider);

		// --- TAG Status ---
		Status status = createStatus();

		// --- TAG Assertion ---
		Assertion assertion = generateAssertion(idp, serviceProvider);

		if(assertion == null) {
			String error = "error building assertion";
			logger.error(error);
			JsonObject jsonObject = new JsonObject();
			jsonObject.putString("error",error);
			return jsonObject;
		}

		// -- attribute Destination (acs) --
		String destination = "https://ts.ac-paris.fr/sso/acs";

		// TODO à récupérer des métadatas du serviceprovider (académie)
			/*AssertionConsumerService assertionConsumerService = entityDescriptorMap.get(serviceProvider).getSPSSODescriptor(SAMLConstants.SAML20P_NS).getDefaultAssertionConsumerService();
			if(assertionConsumerService == null) {
				String error = "error : AssertionConsumerService not found";
				logger.error(error);
				return new JsonObject().putString("error", error);
			}
			destination = assertionConsumerService.getLocation();*/

		// --- Build response --
		Response response = createResponse(new DateTime(), issuer, status, assertion, destination);
//			response.setSignature(signature);

		ResponseMarshaller marshaller = new ResponseMarshaller();
		Element element = marshaller.marshall(response);

//			if (signature != null) {
//				Signer.signObject(signature);
//			}

//			ByteArrayOutputStream baos = new ByteArrayOutputStream();
//			XMLHelper.writeNode(element, baos);

		StringWriter rspWrt = new StringWriter();
		XMLHelper.writeNode(element, rspWrt);

		logger.info("response : "+ rspWrt.toString());
		JsonObject jsonObject = new JsonObject();
		jsonObject.putString("SAMLResponse",rspWrt.toString());
		jsonObject.putString("destination",destination);

		return jsonObject;
	}

	private Status createStatus() {
		StatusCodeBuilder statusCodeBuilder = new StatusCodeBuilder();
		StatusCode statusCode = statusCodeBuilder.buildObject();
		statusCode.setValue(StatusCode.SUCCESS_URI);

		StatusBuilder statusBuilder = new StatusBuilder();
		Status status = statusBuilder.buildObject();
		status.setStatusCode(statusCode);

		return status;
	}

	/*private Signature createSignature() throws Throwable {
		if (publicKeyLocation != null && privateKeyLocation != null) {
			SignatureBuilder builder = new SignatureBuilder();
			Signature signature = builder.buildObject();
			signature.setSigningCredential(certManager.getSigningCredential(publicKeyLocation, privateKeyLocation));
			signature.setSignatureAlgorithm(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA1);
			signature.setCanonicalizationAlgorithm(SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);

			return signature;
		}

		return null;
	}*/

	private Response createResponse(final DateTime issueDate, Issuer issuer, Status status, Assertion assertion, String destination) {
		ResponseBuilder responseBuilder = new ResponseBuilder();
		Response response = responseBuilder.buildObject();
		response.setID(UUID.randomUUID().toString());
		response.setIssueInstant(issueDate);
		response.setVersion(SAMLVersion.VERSION_20);
		response.setIssuer(issuer);
		response.setStatus(status);
		response.setDestination(destination);
		response.getAssertions().add(assertion);
		return response;
	}


	private Assertion generateAssertion(String idp, String serviceProvider)
			throws UnsupportedEncodingException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
		logger.info("start generating assertion");
		logger.info("IDP : " + idp);
		logger.info("SP : " + serviceProvider);

		// Init assertion
		AssertionBuilder assertionBuilder = new AssertionBuilder();

		// --- TAG Assertion ---
		Assertion assertion = assertionBuilder.buildObject();
		// attribut ID
		assertion.setID(UUID.randomUUID().toString());
		logger.info("Assertion ID : " + assertion.getID());

		// attribut IssueInstant
		DateTime authenticationTime = new DateTime();
		assertion.setIssueInstant(authenticationTime);
		logger.info("IssueInstant : " + assertion.getIssueInstant());

		// --- TAG Issuer ---
		// TODO préciser le nom du SP de téléservices
		Issuer issuer = createIssuer(serviceProvider);
		assertion.setIssuer(issuer);

		// --- TAG Subject ---
		// TODO spécifier le nameId + indiquer NameQualifier / SPNameQualifier + Recipient du SubjectConfirmation (l'acs de l'académie)
		Subject subject = createSubject("dadazdadaz", 5);
		assertion.setSubject(subject);

		// --- TAG Conditions ? ---

		// --- TAG AuthnStatement ---
		AuthnStatement authnStatement = createAuthnStatement(authenticationTime);
		assertion.getAuthnStatements().add(authnStatement);

		// --- TAG AttributeStatement ---
		logger.info("create user Vector(s)");
		HashMap<String, List<String>> attributes = new HashMap<String, List<String>>();
		// TODO variabiliser le type de vecteur en fonction du SP
		// TODO construire le ou les vecteurs en fonction de l'utilisateur et de son profil
		// TODO bonus : voir pour mettre le FriendlyName

		attributes.put("FrEduVecteur", Arrays.asList("4|||701294|0790031E"));
		AttributeStatement attributeStatement = createAttributeStatement(attributes);
		assertion.getAttributeStatements().add(attributeStatement);


		// create credential and initialize
		SSODescriptor spSSODescriptor = getSSODescriptor(serviceProvider);
		if(spSSODescriptor == null) {
			logger.error("error SSODescriptor not found for serviceProvider : " + serviceProvider);
			return null;
		}
		//getIDPSSODescriptor("").getKeyDescriptors().get(0).getKeyInfo().getX509Datas().get(0).getX509Certificates().get(0);
		BasicX509Credential credential = new BasicX509Credential();
		credential.setEntityCertificate(x509Certif);
		credential.setPrivateKey(privateKey);

		JsonObject jsonObject = new JsonObject();
		String strAssertion = assertion.toString();
		logger.info("strAssertion : "+ strAssertion);



		return  assertion;
	}

	private AttributeStatement createAttributeStatement(HashMap<String, List<String>> attributes) {
		// create authenticationstatement object
		AttributeStatementBuilder attributeStatementBuilder = new AttributeStatementBuilder();
		AttributeStatement attributeStatement = attributeStatementBuilder.buildObject();

		AttributeBuilder attributeBuilder = new AttributeBuilder();
		if (attributes != null) {
			for (Map.Entry<String, List<String>> entry : attributes.entrySet()) {
				Attribute attribute = attributeBuilder.buildObject();
				attribute.setName(entry.getKey());

				for (String value : entry.getValue()) {
					XSStringBuilder stringBuilder = new XSStringBuilder();
					XSString attributeValue = stringBuilder.buildObject(AttributeValue.DEFAULT_ELEMENT_NAME, XSString.TYPE_NAME);
					attributeValue.setValue(value);
					attribute.getAttributeValues().add(attributeValue);
				}

				attributeStatement.getAttributes().add(attribute);
			}
		}

		return attributeStatement;
	}

	private SSODescriptor getSSODescriptor (String serviceProvider) {
		EntityDescriptor entityDescriptor = entityDescriptorMap.get(serviceProvider);

		if(entityDescriptor == null) {
			return null;
		}

		SPSSODescriptor spSSODescriptor = entityDescriptor.getSPSSODescriptor(SAMLConstants.SAML20P_NS);
		return spSSODescriptor;
	}

	private AuthnStatement createAuthnStatement(final DateTime issueDate) {
		logger.info("createAuthnStatement with issueDate : " + issueDate);
		// create authcontextclassref object
		AuthnContextClassRefBuilder classRefBuilder = new AuthnContextClassRefBuilder();
		AuthnContextClassRef classRef = classRefBuilder.buildObject();
		classRef.setAuthnContextClassRef("urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport");

		// create authcontext object
		AuthnContextBuilder authContextBuilder = new AuthnContextBuilder();
		AuthnContext authnContext = authContextBuilder.buildObject();
		authnContext.setAuthnContextClassRef(classRef);

		// create authenticationstatement object
		AuthnStatementBuilder authStatementBuilder = new AuthnStatementBuilder();
		AuthnStatement authnStatement = authStatementBuilder.buildObject();
		authnStatement.setAuthnInstant(issueDate);
		authnStatement.setAuthnContext(authnContext);

		return authnStatement;
	}

	private Issuer createIssuer(final String issuerName) {
		logger.info("createIssuer : " + issuerName);
		// create Issuer object
		IssuerBuilder issuerBuilder = new IssuerBuilder();
		Issuer issuer = issuerBuilder.buildObject();
		issuer.setValue(issuerName);
		return issuer;
	}

	private Subject createSubject(final String nameIdValue, final Integer samlAssertionDays) {
		logger.info("createSubject : " + nameIdValue);
		logger.info("samlAssertionDays : " + samlAssertionDays);
		DateTime currentDate = new DateTime();
		if (samlAssertionDays != null)
			currentDate = currentDate.plusDays(samlAssertionDays);

		// create name element
		NameIDBuilder nameIdBuilder = new NameIDBuilder();
		NameID nameId = nameIdBuilder.buildObject();
		nameId.setValue(nameIdValue);
		nameId.setFormat("urn:oasis:names:tc:SAML:2.0:nameid-format:persistent");

		SubjectConfirmationDataBuilder dataBuilder = new SubjectConfirmationDataBuilder();
		SubjectConfirmationData subjectConfirmationData = dataBuilder.buildObject();
		subjectConfirmationData.setNotOnOrAfter(currentDate);

		SubjectConfirmationBuilder subjectConfirmationBuilder = new SubjectConfirmationBuilder();
		SubjectConfirmation subjectConfirmation = subjectConfirmationBuilder.buildObject();
		subjectConfirmation.setMethod("urn:oasis:names:tc:SAML:2.0:cm:bearer");
		subjectConfirmation.setSubjectConfirmationData(subjectConfirmationData);

		// create subject element
		SubjectBuilder subjectBuilder = new SubjectBuilder();
		Subject subject = subjectBuilder.buildObject();
		subject.setNameID(nameId);
		subject.getSubjectConfirmations().add(subjectConfirmation);

		return subject;
	}

	private JsonObject generateSimpleSPEntityIDRequest(String idp, String sp) {
		return new JsonObject()
				.putString("authn-request", getAuthnRequestUri(idp) + "?SPEntityID=" + sp)
				.putString("relay-state", SamlUtils.SIMPLE_RS);
	}

	private JsonObject generateAuthnRequest(String idp, String sp, String acs, boolean sign)
			throws NoSuchFieldException, IllegalAccessException, MarshallingException, IOException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
		final String id = "ENT_" + UUID.randomUUID().toString();

		//Create an issuer Object
		Issuer issuer = new IssuerBuilder().buildObject("urn:oasis:names:tc:SAML:2.0:assertion", "Issuer", "samlp" );
		issuer.setValue(sp);

		final NameIDPolicy nameIdPolicy = new NameIDPolicyBuilder().buildObject();
		nameIdPolicy.setFormat("urn:oasis:names:tc:SAML:2.0:nameid-format:transient");
		//nameIdPolicy.setSPNameQualifier("");
		nameIdPolicy.setAllowCreate(true);

		//Create AuthnContextClassRef
		AuthnContextClassRefBuilder authnContextClassRefBuilder = new AuthnContextClassRefBuilder();
		AuthnContextClassRef authnContextClassRef =
				authnContextClassRefBuilder.buildObject("urn:oasis:names:tc:SAML:2.0:assertion",
						"AuthnContextClassRef", "saml");
		authnContextClassRef.setAuthnContextClassRef("urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport");

		RequestedAuthnContext requestedAuthnContext = new RequestedAuthnContextBuilder().buildObject();
		requestedAuthnContext.setComparison(AuthnContextComparisonTypeEnumeration.EXACT);
		requestedAuthnContext.getAuthnContextClassRefs().add(authnContextClassRef);

		final AuthnRequest authRequest =  new AuthnRequestBuilder()
				.buildObject("urn:oasis:names:tc:SAML:2.0:protocol", "AuthnRequest", "samlp");
		authRequest.setForceAuthn(false);
		authRequest.setIsPassive(false);
		authRequest.setIssueInstant(new DateTime());
		authRequest.setProtocolBinding("urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST");
		authRequest.setAssertionConsumerServiceURL(acs);
		authRequest.setAssertionConsumerServiceIndex(1);
		authRequest.setAttributeConsumingServiceIndex(1);
		authRequest.setIssuer(issuer);
		authRequest.setNameIDPolicy(nameIdPolicy);
		authRequest.setRequestedAuthnContext(requestedAuthnContext);
		authRequest.setID(id);
		authRequest.setVersion(SAMLVersion.VERSION_20);

		final String anr = SamlUtils.marshallAuthnRequest(authRequest)
				.replaceFirst("<\\?xml version=\"1.0\" encoding=\"UTF-8\"\\?>\n", "");
		final String rs = UUID.randomUUID().toString();
		String queryString = "SAMLRequest=" + URLEncoder.encode(ZLib.deflateAndEncode(anr), "UTF-8") +
				"&RelayState=" + rs;
		if (sign) {
			queryString = sign(queryString);
		}

		return new JsonObject()
				.putString("id", id)
				.putString("relay-state", rs)
				.putString("authn-request", getAuthnRequestUri(idp) + "?" + queryString);
	}

	private String sign(String c) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException, UnsupportedEncodingException {
		final String content = c + "&SigAlg=http%3A%2F%2Fwww.w3.org%2F2000%2F09%2Fxmldsig%23rsa-sha1";
		java.security.Signature sign = java.security.Signature.getInstance("SHA1withRSA");
		sign.initSign(privateKey);
		sign.update(content.getBytes("UTF-8"));
		return content + "&Signature=" +  URLEncoder.encode(Base64.encodeBytes(sign.sign(), Base64.DONT_BREAK_LINES), "UTF-8");
	}

	private String generateSloRequest(String nameID, String sessionIndex, String idp) throws Exception {
		NameID nId = SamlUtils.unmarshallNameId(nameID);
		NameID nameId = SamlUtils.buildSAMLObjectWithDefaultName(NameID.class);
		nameId.setFormat(nId.getFormat());
		nameId.setValue(nId.getValue());
		String spIssuer = this.issuer;
		if (isNotEmpty(nId.getNameQualifier())) {
			nameId.setNameQualifier(nId.getNameQualifier());
		}
		if (isNotEmpty(nId.getSPNameQualifier())) {
			nameId.setSPNameQualifier(nId.getSPNameQualifier());
			spIssuer = nId.getSPNameQualifier();
		}
		LogoutRequest logoutRequest = SamlUtils.buildSAMLObjectWithDefaultName(LogoutRequest.class);

		logoutRequest.setID("ENT_" + UUID.randomUUID().toString());
		String sloUri = getLogoutUri(idp);
		logoutRequest.setDestination(sloUri);
		logoutRequest.setIssueInstant(new DateTime());

		Issuer issuer = SamlUtils.buildSAMLObjectWithDefaultName(Issuer.class);
		issuer.setValue(spIssuer);
		logoutRequest.setIssuer(issuer);

		SessionIndex sessionIndexElement = SamlUtils.buildSAMLObjectWithDefaultName(SessionIndex.class);

		sessionIndexElement.setSessionIndex(sessionIndex);
		logoutRequest.getSessionIndexes().add(sessionIndexElement);

		logoutRequest.setNameID(nameId);

		String lr = SamlUtils.marshallLogoutRequest(logoutRequest)
				.replaceFirst("<\\?xml version=\"1.0\" encoding=\"UTF-8\"\\?>\n", "");
		logger.info("lr : " + lr);
		String queryString = "SAMLRequest=" + URLEncoder.encode(ZLib.deflateAndEncode(lr), "UTF-8") + "&RelayState=" + config.getString("saml-slo-relayState", "NULL");

			queryString = sign(queryString);

		logger.info("querystring : " + queryString);

		return sloUri + "?" + queryString;
	}

	private String getAuthnRequestUri(String idp) {
		String ssoServiceURI = null;
		EntityDescriptor entityDescriptor = entityDescriptorMap.get(idp);
		if (entityDescriptor != null) {
			for (SingleSignOnService ssos : entityDescriptor.getIDPSSODescriptor(SAMLConstants.SAML20P_NS)
					.getSingleSignOnServices()) {
				if (ssos.getBinding().equals(SAMLConstants.SAML2_REDIRECT_BINDING_URI)) {
					ssoServiceURI = ssos.getLocation();
				}
			}
		}
		return ssoServiceURI;
	}

	private String getLogoutUri(String idp) {
		String sloServiceURI = null;
		EntityDescriptor entityDescriptor = entityDescriptorMap.get(idp);
		if (entityDescriptor != null) {
			for (SingleLogoutService sls : entityDescriptor.getIDPSSODescriptor(SAMLConstants.SAML20P_NS)
					.getSingleLogoutServices()) {
				if (sls.getBinding().equals(SAMLConstants.SAML2_REDIRECT_BINDING_URI)) {
					sloServiceURI = sls.getLocation();
				}
			}
		}
		return sloServiceURI;
	}

	public boolean validateSignature(String assertion) throws Exception {
		final Response response = SamlUtils.unmarshallResponse(assertion);
		final SAMLSignatureProfileValidator profileValidator = new SAMLSignatureProfileValidator();
		Signature signature = response.getSignature();

		if (signature == null) {
			if (response.getAssertions() != null && !response.getAssertions().isEmpty()) {
				for (Assertion a : response.getAssertions()) {
					signature = a.getSignature();
				}
			} else if (response.getEncryptedAssertions() != null && !response.getEncryptedAssertions().isEmpty()) {
				Assertion a = decryptAssertion(response);
				if (a != null) {
					signature = a.getSignature();
				}
			} else {
				throw new ValidationException("Assertions not founds.");
			}
		}
		if (signature == null) {
			throw new ValidationException("Signature not found.");
		}
		profileValidator.validate(signature);

		SignatureTrustEngine sigTrustEngine =  getSignatureTrustEngine(response);
		CriteriaSet criteriaSet = new CriteriaSet();
		criteriaSet.add(new EntityIDCriteria(SamlUtils.getIssuer(response)));
		criteriaSet.add(new MetadataCriteria(IDPSSODescriptor.DEFAULT_ELEMENT_NAME, SAMLConstants.SAML20P_NS));
		criteriaSet.add(new UsageCriteria(UsageType.SIGNING));

		return sigTrustEngine.validate(signature, criteriaSet);
	}

	private void loadSignatureTrustEngine(String filePath) throws MetadataProviderException {
		logger.info(filePath);
		FilesystemMetadataProvider metadataProvider = new FilesystemMetadataProvider(new File(filePath));
		metadataProvider.setParserPool(new BasicParserPool());
		metadataProvider.initialize();
		MetadataCredentialResolver metadataCredResolver = new MetadataCredentialResolver(metadataProvider);
		KeyInfoCredentialResolver keyInfoCredResolver =
				Configuration.getGlobalSecurityConfiguration().getDefaultKeyInfoCredentialResolver();
		EntityDescriptor entityDescriptor = (EntityDescriptor) metadataProvider.getMetadata();
		String entityID = entityDescriptor.getEntityID();
		entityDescriptorMap.put(entityID, entityDescriptor);
		signatureTrustEngineMap.put(entityID,
				new ExplicitKeySignatureTrustEngine(metadataCredResolver, keyInfoCredResolver));
	}

	private SignatureTrustEngine getSignatureTrustEngine(Response response) {
		return signatureTrustEngineMap.get(SamlUtils.getIssuer(response));
	}

	private String decryptAssertion(String response) throws Exception {
		return SamlUtils.marshallAssertion(decryptAssertion(SamlUtils.unmarshallResponse(response)));
	}

	private Assertion decryptAssertion(Response response) throws Exception {
		EncryptedAssertion encryptedAssertion;
		if (response.getEncryptedAssertions() != null && response.getEncryptedAssertions().size() == 1) {
			encryptedAssertion = response.getEncryptedAssertions().get(0);
		} else {
			throw new ValidationException("Encrypted Assertion not found.");
		}

		BasicX509Credential decryptionCredential = new BasicX509Credential();
		decryptionCredential.setPrivateKey(privateKey);

		Decrypter decrypter = new Decrypter(null, new StaticKeyInfoCredentialResolver(decryptionCredential),
				new InlineEncryptedKeyResolver());
		decrypter.setRootInNewDocument(true);

		Assertion assertion = decrypter.decrypt(encryptedAssertion);

		if (assertion != null && assertion.getSubject() != null && assertion.getSubject().getEncryptedID() != null) {
			SAMLObject s = decrypter.decrypt(assertion.getSubject().getEncryptedID());
			if (s instanceof BaseID) {
				assertion.getSubject().setBaseID((BaseID) s);
			} else if (s instanceof NameID) {
				assertion.getSubject().setNameID((NameID) s);
			}
			assertion.getSubject().setEncryptedID(null);
		}

		if (assertion != null && assertion.getAttributeStatements() != null) {
			for (AttributeStatement statement : assertion.getAttributeStatements()) {
				for (EncryptedAttribute ea : statement.getEncryptedAttributes()) {
					Attribute a = decrypter.decrypt(ea);
					statement.getAttributes().add(a);
				}
				statement.getEncryptedAttributes().clear();
			}
		}
		return assertion;
	}

}
