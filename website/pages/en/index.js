/**
 * Copyright (c) 2017-present, Facebook, Inc.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

const React = require('react');

const CompLibrary = require('../../core/CompLibrary.js');

const MarkdownBlock = CompLibrary.MarkdownBlock; /* Used to read markdown */
const Container = CompLibrary.Container;
const GridBlock = CompLibrary.GridBlock;

class HomeSplash extends React.Component {
    render() {
        const {siteConfig, language = ''} = this.props;
        const {baseUrl, docsUrl} = siteConfig;
        const docsPart = `${docsUrl ? `${docsUrl}/` : ''}`;
        const langPart = `${language ? `${language}/` : ''}`;
        const docUrl = doc => `${baseUrl}${docsPart}${langPart}${doc}`;

        const SplashContainer = props => (
            <div className="homeContainer">
                <div className="homeSplashFade">
                    <div className="wrapper homeWrapper">{props.children}</div>
                </div>
            </div>
        );

        const Logo = props => (
            <div className="projectLogo">
                <img src={props.img_src} alt="Project Logo"/>
            </div>
        );

        const ProjectTitle = () => (
            <h2 className="projectTitle">
                {siteConfig.title}
                <small>{siteConfig.tagline}</small>
            </h2>
        );

        const PromoSection = props => (
            <div className="section promoSection">
                <div className="promoRow">
                    <div className="pluginRowBlock">{props.children}</div>
                </div>
            </div>
        );

        const Button = props => (
            <div className="pluginWrapper buttonWrapper">
                <a className="button" href={props.href} target={props.target}>
                    {props.children}
                </a>
            </div>
        );

        return (
            <SplashContainer>
                <div className="inner">
                    <ProjectTitle siteConfig={siteConfig}/>
                    <PromoSection>
                        <Button href={docUrl('overview/overview_index')}>Overview</Button>
                        <Button href={docUrl('usecases/usecases_index')}>Use Cases</Button>
                        <Button href="https://github.com/zio/zio-actors" target="_blank">GitHub</Button>
                    </PromoSection>
                </div>
            </SplashContainer>
        );
    }
}

class Index extends React.Component {
    render() {
        const {config: siteConfig, language = ''} = this.props;
        const {baseUrl} = siteConfig;

        const Block = props => (
            <Container
                padding={['bottom', 'top']}
                id={props.id}
                background={props.background}>
                <GridBlock
                    align="center"
                    contents={props.children}
                    layout={props.layout}
                />
            </Container>
        );

        const FeatureCallout = () => (
            <div
                className="productShowcaseSection paddingBottom"
                style={{textAlign: 'center'}}>
                <h2>Welcome to ZIO Actors</h2>
                <MarkdownBlock>
                    A high-performance, purely-functional library for building, composing, and supervising typed actors
                    backed by `ZIO`.
                </MarkdownBlock>

                <MarkdownBlock>
                    The [Actor Model](https://en.wikipedia.org/wiki/Actor_model) is used to build distributed highly
                    scalable applications.
                    The core concept behind the actor model is the ability to create multiple actors which run
                    concurrently.
                    The actor would receive a message do some computation on the message and then output a new message.
                    Each actor runs independently of each other with no shared state between them and as such failure of
                    one actor won't have an affect on the running of another.
                    In its simplest form the goal of this project is to provide the ability to write actors in
                    Functional Way that are typed leveraging [ZIO](https://github.com/zio/zio).
                </MarkdownBlock>

                <h2>
                    ZIO Actors current alternatives
                </h2>

                <MarkdownBlock>
                    - [Akka](https://akka.io) (Scala & Java)
                </MarkdownBlock>

                <MarkdownBlock>
                    - [Akka .net](https://getakka.net) (C#)
                </MarkdownBlock>

                <MarkdownBlock>
                    - [Orleans](https://dotnet.github.io/orleans/) (C#)
                </MarkdownBlock>

                <MarkdownBlock>
                    - [Erlang/Otp](http://www.erlang.org) (Erlang)
                </MarkdownBlock>

                <MarkdownBlock>
                    - [Elixir](https://elixir-lang.org) (Elixir)
                </MarkdownBlock>

                <MarkdownBlock>
                    We differentiate ourselves from the above competition by having the following benefits:
                    * Purely Functional
                    * Everything Typed
                    * Light Weight
                </MarkdownBlock>
            </div>
        );

        const Features = () => (
            <Block layout="fourColumn">
                {[
                    {
                        content: 'Model actors\' communication without side effects',
                        image: `${baseUrl}img/undraw_tweetstorm.svg`,
                        imageAlign: 'top',
                        title: 'Effectful',
                    },
                    {
                        content: 'Fully typed - with message, response and error type',
                        image: `${baseUrl}img/undraw_operating_system.svg`,
                        imageAlign: 'top',
                        title: 'Typed',
                    },
                ]}
            </Block>
        );

        return (
            <div>
                <HomeSplash siteConfig={siteConfig} language={language}/>
                <div className="mainContainer">
                    <Features/>
                    <FeatureCallout/>
                </div>
            </div>
        );
    }
}

module.exports = Index;
