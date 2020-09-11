import React, { Component } from 'react';
import 'react-vis/dist/style.css'
import 'grommet-css';
import 'antd/dist/antd.css';
import './css/App.css';
import 'jointjs/dist/joint.css';
import i18n from './i18n.js';
import ru_RU from 'antd/lib/locale-provider/ru_RU';
import en_US from 'antd/lib/locale-provider/en_US';
import Navigation from './components/Navigation.js';
import Bottom from './components/Bottom.js';
import SplitPane from 'react-split-pane';
import Pane from 'react-split-pane/lib/Pane';
import LeftPanel from './components/LeftPanel.js';
import resource from './Resource'
import Login from './components/Login.js';
import StartPage from './components/StartPage.js';
import update from 'immutability-helper'
import createComponent from './components/Components'
import {LocaleProvider, Layout, Menu, Avatar, notification, Tooltip, Button} from 'antd';
import { getClassDef } from './model'
import _ from 'lodash'
import { addObjectToPath, buildPath, createHRef } from './utils/encode'

const { Header, Content, Footer, Sider } = Layout;

const AppContext = React.createContext();

class App extends Component {

    constructor(...args) {
        super(...args);
        this.state = {
            path: [],
            waitMinute: true,
            pathHistory: JSON.parse(localStorage.getItem("pathHistory") || "[]"),
            locale: null,
            stompConnected: false,
            showLogin: false,
            context: {
                updateContext: (context, cb) => {
                    this.setState((state, props) => {
                        return { context: update(state.context, { $merge: context }) }
                    }, cb)
                },
                setCurrentBranch: this.setCurrentBranch,
                branchInfo: {
                    current: null,
                    branches: {
                    }
                }
            },
            searchStr: "",
            splitterPosition: "0%",
            bottomComponents:
                [
                    {
                        id: 'logs', name: 'Logs', menuItem: () => {
                            return <span><Tooltip placement="bottom" title={i18n.t('logs')}><Avatar
                                src="images/icon-core/content.svg" size={"small"} /></Tooltip></span>
                        }, props: {}
                    },
                    {
                        id: 'processes', name: 'Processes', menuItem: () => {
                            return <span><Tooltip placement="bottom"
                                title={i18n.t('processes')}>{this.state.context.fetchCount > 0 ?
                                    <Avatar src="images/icon-core/setting-anim.svg" size={"small"} /> :
                                    <Avatar src="images/icon-core/setting.svg" size={"small"} />}</Tooltip></span>
                        }, props: {}
                    },
                    {
                        id: 'explore', name: 'ObjectExplorer', menuItem: () => {
                            return <span><Tooltip placement="bottom" title={i18n.t('explore')}><Avatar
                                src="images/icon-core/tree.svg" size={"small"} /></Tooltip></span>
                        }, props: {}
                    },
                    {
                        id: 'search', name: 'Search', menuItem: () => {
                            return <span><Tooltip placement="bottom" title={i18n.t('search')}><Avatar
                                src="images/icon-core/search.svg" size={"small"} /></Tooltip></span>
                        }, props: {}
                    }
                ]
        }
    }

    setLang(lang) {
        i18n.changeLanguage(lang)
        this.getLocale()
    }

    getLocale() {
        let localeValue
        i18n.language === 'ru-RU' ? localeValue = ru_RU : localeValue = en_US
        this.setState({ locale: localeValue })
    }

    selectObject(object, options) {
        options = options || {}
        const searchParams = new URLSearchParams(this.props.location.search)
        let path = buildPath(searchParams)
        path = addObjectToPath(path, object, options)
        const args = options.args || {}
        const params = !options.keepSearch ? {} : [...searchParams.keys()].filter(key => key !== "path").reduce((map, key) => {
            return { ...map, [key]: searchParams.get(key) }
        }, {})
        this.push(path, { ...params, ...args })
    }

    onPropsSetOrChange(props) {
        const search = props.location.search
        const urlParams = new URLSearchParams(search)
        const path = buildPath(urlParams)
        const activeObject = path[path.length - 1]
        let pathHistory = this.state.pathHistory
        if (activeObject.e_id) {
            pathHistory = [path.slice(1), ...pathHistory.filter(p => p[p.length - 1].e_id !== activeObject.e_id || p[p.length - 1]._type_ !== activeObject._type_)]
            const delta = pathHistory.length - 10
            if (delta > 0) {
                pathHistory.splice(10, delta)
            }
            localStorage.setItem("pathHistory", JSON.stringify(pathHistory))
        }
        this.setState({ activeObject, searchStr: "", path, urlParams, pathHistory });
    }

    componentWillReceiveProps(nextProps) {
        this.onPropsSetOrChange(nextProps)
    }

    push(path, args) {
        const href = createHRef(this.props.location.pathname, path, args)
        this.props.history.push(href)
    }

    mainView() {
        const containerName = _.get(getClassDef(_.get(this.state, ["activeObject", "_type_"])), "containerName")
        return (
                <div>
                {createComponent(containerName,
                    {
                        ...this.props,
                        onSelectObject: (object, options) => {
                            this.selectObject(object, options)
                        },
                        activeObject: this.state.activeObject,
                        addBottomItem: (...item) => {
                            this.setState(update(this.state, { bottomComponents: { $push: [...item] } }))
                        },
                        urlParams: this.state.urlParams,
                        pathHistory: this.state.pathHistory,
                        push: path => this.push(path),
                        removeBottomItem: (...item) => {
                            const list = this.state.bottomComponents
                            const newList = _.difference(list, item)
                            this.setState({ bottomComponents: newList })
                        },
                        updateItemProps: (item, newProps) => {
                            var list = this.state.bottomComponents
                            var idx = list.findIndex((i) => {
                                return i.id === item.id
                            })
                            this.setState(update(this.state, { bottomComponents: { [idx]: { props: { $set: newProps } } } }))
                        }
                    })}
            </div>
        )
    }

    bottomPanel() {
        return (
            <Menu mode="horizontal" style={
                {
                    left: this.state.collapsed ? "0px" : "300px",
                    width: this.state.collapsed ? "calc(100vw - 24px)" : "calc(100vw - 330px)"
                }
            }
                onClick={(e) => {
                    if (e.key !== "trigger") {
                        this.setState({
                            activeBottom: this.state.activeBottom === e.key ? undefined : e.key,
                            splitterPosition: this.state.activeBottom === e.key ? '0%' : '40%',
                        })
                    }
                }
                }
                selectedKeys={this.state.activeBottom ? [this.state.activeBottom] : []}>
                <Menu.Item key={"trigger"} style={{ "borderBottom": "1px solid transparent" }}>
                    <Tooltip title={this.state.collapsed ? i18n.t('show') : i18n.t('hide')}>
                        <Avatar

                            src={"images/icon-core/" + (this.state.collapsed ? 'menu-unfold.svg' : 'menu-fold.svg')}
                            onClick={() => this.setState({ collapsed: !this.state.collapsed })}
                        />
                    </Tooltip>
                </Menu.Item>
                {this.state.bottomComponents.filter(c => !c.component || (c.component && this.state.activeObject._type_ === "etl.Transformation")).map((item) => {
                    return (
                        <Menu.Item key={item.id}>
                            {item.menuItem()}
                        </Menu.Item>
                    )
                })}
            </Menu>
        )
    }

    handleSearchChange = (searchStr) => {
        var search = function () {
            this.activeSearch = undefined
            this.setState({ searchStr: this.toSearch });
        }.bind(this)
        this.toSearch = searchStr.target.value
        if (this.activeSearch === undefined) {
            this.activeSearch = search
            setTimeout(search, 700)
        }
    }

    getIcon(m) {
        var icon = m.icon
        if (!icon) {
            var type = m._type_ === "ecore.EClass" ? m.name : m._type_
            if (type) {
                icon = _.get(getClassDef(type), 'icon', "arrow-right.svg")
            }
        }
        return ("images/icon-core/" + icon)
    }

    layout() {
        const { activeBottom, splitterPosition } = this.state;
        return (
            <Layout style={{ minHeight: "100vh" }}>
                <Header>
                    <AppContext.Consumer>{context =>
                        <Navigation {...this.props}
                                    context={context}
                                    path={this.state.path}
                                    activeObject={this.state.activeObject}
                                    onSelectObject={(object, options) => {
                                        this.selectObject(object, options)
                                    }}
                                    user={this.state.user}
                                    stompConnected={this.state.stompConnected}
                                    login={(show) => {
                                        this.login(show)
                                    }}
                                    setLang={(lang) => {
                                        this.setLang(lang)
                                    }}
                                    resource={resource}
                                    getIcon={this.getIcon}
                                    pathHistory={this.state.pathHistory}
                                    push={path => this.push(path)}
                        />
                    }</AppContext.Consumer>
                </Header>
                <Layout>
                    <Sider width={300} collapsedWidth={0}
                        collapsed={this.state.collapsed} onCollapse={collapsed => this.setState({ collapsed })}
                        className={"left-sider"}>
                        <LeftPanel
                            activeObject={this.state.activeObject}
                            onSelectObject={(object) => {
                                this.selectObject(object)
                            }}
                            searchStr={this.state.searchStr}
                            leftComponents={this.state.leftComponents}
                            handleSearchChange={this.handleSearchChange}
                            collapsed={this.state.collapsed}
                            getIcon={this.getIcon}
                            t={this.props.t}
                        />
                    </Sider>
                    <Content className={"child-content"}>
                        <div style={{ height: 'calc(100vh - 72px)' }}>
                            <SplitPane
                                split="horizontal"
                                primary="first"
                                pane1Style={{ height: '100%' }}
                                pane2Style={{ height: '100%' }}
                                allowResize={activeBottom ? true : false}
                                className={activeBottom ? 'splitter-bottom-active' : 'splitter-bottom-unactive'}
                            >
                                <Pane>
                                    <div style={{ height: '101%', overflow: 'auto' }}>
                                        {this.mainView()}
                                    </div>
                                </Pane>
                                <Pane
                                    className={"splitter-pane"}
                                    initialSize={splitterPosition}
                                >
                                    {activeBottom &&
                                        <div style={{ backgroundColor: 'white', height: '100%', overflow: 'auto' }}>
                                            <Bottom
                                                components={this.state.bottomComponents}
                                                panel={activeBottom}
                                                processes={resource.processes.map(item => `[${item.type}] ${JSON.stringify(item.props)}`)}
                                                selectObject={path => this.push(path)}
                                                activeObject={this.state.activeObject}
                                            />
                                        </div>}
                                </Pane>
                            </SplitPane>
                        </div>
                    </Content>
                </Layout>
                <Footer className="ant-layout-header">
                    {this.bottomPanel()}
                </Footer>
            </Layout>
        )
    }

    render() {
        const { activeObject } = this.state
        const getActiveObject = _.get(activeObject, '_type_', 'ui3.Application')
        return (
            <AppContext.Provider value={this.state.context}>
                <LocaleProvider locale={this.state.locale}>
                    {this.state.user === undefined ?
                        this.state.waitMinute ?
                            <div className="loader">
                                <div className="inner one"></div>
                                <div className="inner two"></div>
                                <div className="inner three"></div>
                            </div>
                            :
                        (<Login user={this.state.user} userName={this.state.userName} password={this.state.password}
                            showLogin={this.state.showLogin}
                            setLang={(lang) => {
                                this.setLang(lang)
                            }}
                            setState={(state) => {
                                this.setState(Object.assign({}, this.state, state))
                            }} />)
                        :
                        (
                            getActiveObject === 'ui3.Application' ?
                                <StartPage
                                    resource={resource}
                                    activeObject={this.state.activeObject}
                                    pathHistory={this.state.pathHistory}
                                    push={path => this.push(path)}
                                    onSelectObject={(object) => {
                                        this.selectObject(object)
                                    }}
                                    setLang={(lang) => {
                                        this.setLang(lang)
                                    }}
                                />
                                :
                                <Layout>
                                    {this.layout()}
                                </Layout>
                        )
                    }
                </LocaleProvider>
            </AppContext.Provider>
        );
    }

    updateDimensions() {
        this.setState({ innerWidth: window.innerWidth, innerHeight: window.innerHeight });
    }

    componentWillMount() {
        this.updateDimensions()
        this.getLocale()
    }

    componentWillUnmount() {
        window.removeEventListener("resize", this.updateDimensions);
    }

    setCurrentBranch = (currentBranch, cb) => {
        if (!currentBranch) {
            currentBranch = localStorage.getItem("currentBranch") || "master"
        }
        resource.query(`/system/branch/${currentBranch}`, {
            method: "PUT",
            headers: {
                'Content-Type': 'application/json'
            }
        }).then(branchInfo=>{
            this.state.context.updateContext({branchInfo}, cb)
            localStorage.setItem("currentBranch", branchInfo.current)
        });
    }

    componentDidMount() {
        window.addEventListener("resize", () => this.updateDimensions());
        this.onPropsSetOrChange(this.props)
        resource.setStompConnectedCB((stompConnected) => {
            if (!stompConnected && resource.stompReconnect && resource.stompReconnectCount === 0) {
                resource.logLog("Server connection lost. Reconnecting...")
            }
            this.setState({ stompConnected })
        })

        resource.loginCB = (show) => {
            this.setState({ showLogin: show })
        }
        resource.loginSuccessCB = (user) => {
            this.setState({ user }, ()=>{this.setCurrentBranch(null)})
        }
        resource.loginCB = (showLogin) => {
            if (showLogin) {
                this.setState({ user: undefined })
            }
        }
        resource.setAlertCB((info, headline) => {
            let btn = (<Button type="link" size="small" onClick={() => notification.destroy()}>
                Close All
            </Button>);
            let key = info;
            notification["info"]({
                message: headline,
                btn,
                description: info,
                key,
                duration: 0,
                style: { width: 600, marginLeft: 335 - 600 }
            })
        }, (error, headline) => {
            let btn = (<Button type="link" size="small" onClick={() => notification.destroy()}>
                Close All
            </Button>);
            let key = error;
            notification["error"]({
                message: headline,
                btn,
                description: error,
                key,
                duration: 0,
                style: { width: 600, marginLeft: 335 - 600 }
            })
        })
        resource.authenticate().then(()=>{
            this.setState({waitMinute: false})
        })
        resource.setFetchCountCB((fetchCount) => {
            this.state.context.updateContext({ fetchCount })
        })
    }

    login(show) {
        this.setState({ showLogin: show })
    }

}

export { App as default, AppContext };
