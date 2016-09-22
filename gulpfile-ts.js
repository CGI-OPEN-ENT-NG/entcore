var gulp = require('./gulpfile-loader')('ts');
var ts = require('gulp-typescript');
var webpack = require('webpack-stream');
var bower = require('gulp-bower');
var merge = require('merge2');
var watch = require('gulp-watch');
var rev = require('gulp-rev');
var revReplace = require("gulp-rev-replace");
var clean = require('gulp-clean');
var sourcemaps = require('gulp-sourcemaps');

var apps = ['auth'];

function compileTs(){
    var stream = [];
    apps.forEach((app) => {
        console.log('./' + app + '/src/main/resources/public/ts/tsconfig.json')
        var tsProject = ts.createProject('./' + app + '/src/main/resources/public/ts/tsconfig.json');
        var tsResult = tsProject.src()
            .pipe(sourcemaps.init())
            .pipe(ts(tsProject));
            
        stream.push(
            tsResult.js
                .pipe(sourcemaps.write('.'))
                .pipe(gulp.dest('./' + app + '/src/main/resources/public/temp'))
        );
    });

    return merge(stream);
}

function startEntCoreWebpack(isLocal) {
    var streams = [];

    apps.forEach((app) => {
        var entcoreWebpack = gulp.src('./' + app + '/src/main/resources/public')
            .pipe(webpack(require('./' + app + '/webpack-entcore.config.js')))
            .pipe(gulp.dest('./' + app + '/src/main/resources/public/dist/entcore'))
            .pipe(rev())
            .pipe(gulp.dest('./' + app + '/src/main/resources/public/dist/entcore'))
            .pipe(rev.manifest('./' + app + '/rev-manifest.json', { merge: true }))
            .pipe(gulp.dest('./' + app));
        streams.push(entcoreWebpack);
    });
    
    return merge(streams);
}

function startWebpack(isLocal) {
    var streams = [];

    apps.forEach((app) => {
        var appWebpack = gulp.src('./' + app + '/src/main/resources/public')
            .pipe(webpack(require('./' + app + '/webpack.config.js')))
            .pipe(gulp.dest('./' + app + '/src/main/resources/public/dist'))
            .pipe(rev())
            .pipe(gulp.dest('./' + app + '/src/main/resources/public/dist'))
            .pipe(rev.manifest('./' + app + '/rev-manifest.json', { merge: true }))
            .pipe(gulp.dest('./'));
        streams.push(appWebpack);
    });
    
    return merge(streams);
}

function updateRefs() {
    var streams = [];
    apps.forEach((app) => {

        var stream = gulp.src('./' + app + '/src/main/resources/view-src/**/*.html', { base: './' + app + '/src/main/resources/view-src' })
            .pipe(revReplace({manifest: gulp.src("./" + app + "/rev-manifest.json") }))
            .pipe(gulp.dest("./" + app + "/src/main/resources/view"));
        streams.push(stream);
    });
    return merge(streams);
}

gulp.task('clean-old', () => {
    var streams = [];
    apps.forEach((app) => {
        var appClean = gulp.src([
                './' + app + '/src/main/resources/public/**/*.map',
                './' + app + '/src/main/resources/public/temp',
                './' + app + '/src/main/resources/public/dist',
                '!./' + app + '/src/main/resources/public/dist/application.js'
        ])
		    .pipe(clean());
        streams.push(appClean);
    })
    return merge(streams);
});

gulp.task('copy-local-libs', () => {
    var streams = [];

    var entcore = gulp.src(gulp.local.paths.infraFront + '/src/ts/**/*.ts')
            .pipe(gulp.dest('./node_modules/entcore'));
    streams.push(entcore);

    var toolkit = gulp.src(gulp.local.paths.toolkit + '/src/**/*.ts')
            .pipe(gulp.dest('./node_modules/toolkit'));
    streams.push(toolkit);

    apps.forEach((app) => {
        var toolkit = gulp.src(gulp.local.paths.toolkit + '/src/**/*.ts')
            .pipe(gulp.dest('./' + app + '/src/main/resources/public/ts/toolkit'));

        var ts = gulp.src(gulp.local.paths.infraFront + '/src/ts/**/*.ts')
            .pipe(gulp.dest('./' + app + '/src/main/resources/public/ts/entcore'));

        var html = gulp.src(gulp.local.paths.infraFront + '/src/template/**/*.html')
            .pipe(gulp.dest('./' + app + '/src/main/resources/public/template/entcore'));

        streams.push(toolkit);
        streams.push(ts);
        streams.push(html);
    });
    
    return merge(streams);
});

gulp.task('drop-cache', function(){
    var streams = [];
    apps.forEach((app) => {
        var str = gulp.src(['./bower_components', './' + app + '/src/main/resources/public/dist'], { read: false })
		    .pipe(clean());
        streams.push(str);
    });
    return merge(streams);
});

gulp.task('bower', ['drop-cache'], function(){
    return bower({ directory: './bower_components', cwd: '.', force: true });
});

gulp.task('update-libs', ['bower'], function(){
    var streams = [];
    apps.forEach((app) => {
        var html = gulp.src('./bower_components/entcore/template/**/*.html')
            .pipe(gulp.dest('./' + app + '/src/main/resources/public/template/entcore'));
            
        var ts = gulp.src('./bower_components/entcore/src/ts/**/*.ts' )
            .pipe(gulp.dest('./' + app + '/src/main/resources/public/ts/entcore'));

        var entcore = gulp.src('./bower_components/entcore/src/ts/**/*.ts')
            .pipe(gulp.dest('./node_modules/entcore'));

        var toolkitDefs = gulp.src('./bower_components/toolkit/src/**/*.ts' )
            .pipe(gulp.dest('./' + app + '/src/main/resources/public/ts/toolkit'));

        var toolkit = gulp.src('./bower_components/toolkit/src/**/*.ts')
            .pipe(gulp.dest('./node_modules/toolkit'));

        streams.push(html);
        streams.push(ts);
        streams.push(entcore);
        streams.push(toolkit);
        streams.push(toolkitDefs);
    });
        
    return merge(streams);
});


gulp.task('ts', ['update-libs'], function () { return compileTs() });
gulp.task('ts-local', ['copy-local-libs'], function () { return compileTs() });

gulp.task('webpack-local', ['ts-local'], function(){ return startWebpack() });
gulp.task('webpack', ['ts'], function(){ return startWebpack() });

gulp.task('webpack-entcore-local', ['webpack-local'], function(){ return startEntCoreWebpack() });
gulp.task('webpack-entcore', ['webpack'], function(){ return startEntCoreWebpack() });

gulp.task('drop-temp', ['webpack-entcore'], () => {
    var streams = [];
    apps.forEach((app) => {
        var appClean = gulp.src([
                './' + app + '/src/main/resources/public/**/*.map.map',
                './' + app + '/src/main/resources/public/temp',
                './' + app + '/src/main/resources/public/dist/entcore/ng-app.js',
                './' + app + '/src/main/resources/public/dist/entcore/ng-app.js.map',
                './' + app + '/src/main/resources/public/dist/application.js',
                './' + app + '/src/main/resources/public/dist/application.js.map'
            ], { read: false })
		    .pipe(clean());
        streams.push(appClean);
    })
    merge(streams);
})

gulp.task('build', ['drop-temp'], function () {
    var streams = [];
    streams.push(updateRefs());
    apps.forEach((app) => {
        var copyBehaviours = gulp.src('./' + app + '/src/main/resources/public/dist/behaviours.js')
            .pipe(gulp.dest('./' + app + '/src/main/resources/public/js'));
        var copyWidgets = gulp.src('./src/main/resources/public/temp/widgets/*.js')
            .pipe(gulp.dest('./src/main/resources/public/js'));
        streams.push(copyBehaviours);
        streams.push(copyWidgets);
    })
    
    return merge(streams);
});

gulp.task('build-local', ['webpack-entcore-local'], function () {
    var streams = [];
    streams.push(updateRefs());
    apps.forEach((app) => {
        var copyBehaviours = gulp.src('./' + app + '/src/main/resources/public/dist/behaviours.js')
            .pipe(gulp.dest('./' + app + '/src/main/resources/public/js'));
        var copyWidgets = gulp.src('./src/main/resources/public/temp/widgets/*.js')
            .pipe(gulp.dest('./src/main/resources/public/js'));
        streams.push(copyBehaviours);
        streams.push(copyWidgets);
    })
    
    return merge(streams);
});
