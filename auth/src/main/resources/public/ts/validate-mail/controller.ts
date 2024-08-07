import { IIdiom, IUserInfo } from 'ode-ts-client';
import { session, conf } from 'ode-ngjs-front';
import { IController, IScope } from 'angular';

export class AppController implements IController {
	me: IUserInfo;
	currentLanguage: string;
	lang: IIdiom;
	fullscreen = true;
	force = false;
	step:ValidationStep = "input";
	redirect?:string;
	type:ValidationType = "email";

	constructor(
		private $scope:IScope
		) {
	}

	// IController implementation
	$onInit(): void {
		const platformConf = conf().Platform;
		this.me = session().user;
		this.currentLanguage = session().currentLanguage;
		this.lang = platformConf.idiom;

		const params = (new URL(document.location.href)).searchParams;
		if( params.get('headless') ) {
			this.fullscreen = false;
		}
		if( params.get("step") == "code" ) {
			this.step = 'code';
		}
		if( params.get("force") == "true" ) {
			this.force = true;
		}
		if( params.get("redirect") ) {
			this.redirect = params.get("redirect");
		}
		const paramType = params.get("type");
		if( paramType == "email" || paramType == "sms") {
			this.type = paramType;
		}
	}

};