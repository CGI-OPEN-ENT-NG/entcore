var webpack = require('webpack');
var path = require('path');

module.exports = {
    entry: {
        controller: './src/main/resources/public/temp/app.js',
        behaviours: './src/main/resources/public/temp/behaviours.js'
    },
    output: {
        filename: 'application.js',
        path: __dirname + 'dest'
    },
    externals: {
        "entcore/entcore": "entcore",
        "entcore/libs/moment/moment": "entcore",
        "entcore/libs/underscore/underscore": "_"
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