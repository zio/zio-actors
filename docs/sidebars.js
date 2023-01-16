const sidebars = {
  sidebar: [
    {
      type: "category",
      label: "ZIO Actors",
      collapsed: false,
      link: { type: "doc", id: "index" },
      items: [
        "basics",
        "supervision",
        "remoting",
        "persistence",
        "akka-interop",
        "examples"
      ]
    }
  ]
};

module.exports = sidebars;
