CONFIGURE CERTIFICATE TO SIGN THE APP

GENERATE KEY WITH keytool
-----------------------

	keytool.exe -genkey -v -keystore vger32app_keystore.jks -keyalg RSA -keysize 2048 -validity 3650 -alias vger32
	
	copy to vger32\android\certificates

UPDATE "signing.properties"
---------------------------------------

	storeFile=D:\\sources\\vger32\\android\\certificates\\vger32app_keystore.jks
	storePassword=s3cr3t
	keyAlias=vger32
	keyPassword=s3cr3t

RUN gradlew.bat
--------------------

	./gradlew.bat assembleRelease