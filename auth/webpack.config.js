var webpack = require('webpack');
var path = require('path');

module.exports = {
    entry: {
        controller: './auth/src/main/resources/public/temp/app.js',
        behaviours: './auth/src/main/resources/public/temp/behaviours.js'
    },
    output: {
        filename: 'application.js',
        path: __dirname + 'dest'
    },
    externals: {
        "entcore/entcore": "entcore",
        "entcore/libs/moment/moment": "entcore",
        "entcore/libs/underscore/underscore": "_",
        "entcore/libs/jquery/jquery": "entcore"
    },
    resolve: {
        root: path.resolve(__dirname),
        extensions: ['', '.js']
    },
    devtool: "source-map",
    module: {
        preLoaders: [
            {
                test: /\.js$/,
                loader: 'source-map-loader'
            }
        ]
    }
}