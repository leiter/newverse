# Shared release-tagging helper, required by both androidApp/fastlane/Fastfile and
# iosApp/fastlane/Fastfile.
#
# Why this exists: the store gives you a build number in crash reports, never a
# commit. Without a tag, "which tree produced TestFlight build 46?" can only be
# answered by reading the log by hand -- see the pre-tag commits (501683f
# "iOS build 46 for TestFlight", 7414300 "Ios rbuild version bump").
#
# Deliberately implemented with plain `git` calls rather than fastlane's
# add_git_tag / push_git_tags actions, so the module works when called from a
# module function (no lane context / action runner needed) and so it can be
# unit-checked outside fastlane.
module ReleaseTag
  module_function

  # fastlane exposes its logger as FastlaneCore::UI -- there is no top-level ::UI
  # constant, so a bare `UI.success` inside this module raises NameError. That is
  # not theoretical: it fired *after* a successful TestFlight upload of build 47,
  # after the version-bump commit, and cost that build its tag. Resolve the logger
  # at call time and fall back to stdout so the module also runs outside fastlane.
  module StdoutUI
    module_function

    def message(m);   puts "[release_tag] #{m}"; end
    def success(m);   puts "[release_tag] #{m}"; end
    def important(m); puts "[release_tag] #{m}"; end
    def error(m);     warn "[release_tag] #{m}"; end
  end

  def ui
    return FastlaneCore::UI if defined?(FastlaneCore::UI)
    return ::UI if defined?(::UI)
    StdoutUI
  end

  # Tag only after a successful upload -- a tag for a binary that never reached
  # the store is worse than no tag -- so every caller invokes this *after*
  # upload_to_testflight / upload_to_app_store / upload_to_play_store returns.
  #
  # name:          the tag, e.g. "ios-46" or "android-buy-37"
  # message:       annotation body (annotated tags carry date + author; lightweight ones don't)
  # version_files: repo-root-relative paths the bump lanes rewrite. If dirty they
  #                are committed before tagging, otherwise the tag would point at
  #                a tree still holding the *previous* version number.
  def tag!(name:, message:, version_files: [])
    root = repo_root
    return if root.nil?

    if tag_exists?(root, name)
      ui.important("Tag #{name} already exists -- leaving it pointing where it is.")
      ui.important("The upload succeeded; only the tag was skipped.")
      return
    end

    commit_version_bump(root, name, version_files)
    warn_about_unrelated_changes(root, version_files)

    _, ok = git(root, "tag", "-a", name, "-m", message)
    unless ok
      ui.error("Could not create tag #{name}. The upload itself succeeded.")
      return
    end
    ui.success("Tagged #{current_sha(root)} as #{name}")

    push_tag(root, name)
  end

  # --- internals -------------------------------------------------------------

  # Returns output verbatim -- `git status --porcelain` encodes the status in the
  # first three columns, so stripping here would eat the leading space of the
  # first entry and shift its path by one character.
  def git(root, *args)
    out = IO.popen(["git", "-C", root, *args], err: [:child, :out], &:read)
    [out.to_s, $?.success?]
  end

  def repo_root
    out, ok = git(__dir__, "rev-parse", "--show-toplevel")
    out = out.strip
    unless ok
      ui.error("Not inside a git repository -- skipping release tag.")
      return nil
    end
    out
  end

  def tag_exists?(root, name)
    _, ok = git(root, "rev-parse", "--verify", "--quiet", "refs/tags/#{name}")
    ok
  end

  def current_sha(root)
    sha, = git(root, "rev-parse", "--short", "HEAD")
    sha.strip
  end

  def dirty_files(root)
    out, = git(root, "status", "--porcelain")
    out.lines.map do |line|
      path = line.chomp[3..].to_s
      path = path.split(" -> ").last.to_s   # renames: "R  old -> new"
      path.strip.delete_prefix('"').delete_suffix('"')
    end.reject(&:empty?)
  end

  # The bump lanes rewrite version.properties / project.pbxproj as a side effect of
  # building, so at upload time those edits are usually still uncommitted. Commit
  # exactly those files -- nothing else -- so the tag names a tree that really does
  # contain the version that was just uploaded.
  def commit_version_bump(root, name, version_files)
    dirty = dirty_files(root)
    pending = version_files.select { |f| dirty.include?(f) }
    return if pending.empty?

    git(root, "add", "--", *pending)
    _, ok = git(root, "commit", "-m", "Version bump for #{name}", "--only", "--", *pending)
    if ok
      ui.success("Committed version bump for #{name}: #{pending.join(', ')}")
    else
      ui.important("Could not commit #{pending.join(', ')} -- tagging HEAD as-is.")
    end
  end

  # Anything else uncommitted went into the uploaded binary but will not be in the
  # tagged tree, so the tag is a partial record. Still tag: an uploaded build with
  # no tag at all is the worse outcome. Just say so loudly.
  def warn_about_unrelated_changes(root, version_files)
    others = dirty_files(root) - version_files
    return if others.empty?

    ui.important("Uncommitted changes are NOT part of tag -- the uploaded build was made from a tree containing:")
    others.first(20).each { |f| ui.important("  #{f}") }
    ui.important("  ... and #{others.size - 20} more") if others.size > 20
  end

  # Tags that live only on this machine answer nobody's question later. Never fail
  # the lane over it, though -- the store upload has already happened and cannot be
  # taken back.
  def push_tag(root, name)
    if ENV["SKIP_TAG_PUSH"]
      ui.important("SKIP_TAG_PUSH set -- #{name} exists locally only. Push it with: git push origin #{name}")
      return
    end

    remote = ENV["TAG_REMOTE"] || "origin"
    out, ok = git(root, "push", remote, "refs/tags/#{name}")
    if ok
      ui.success("Pushed #{name} to #{remote}")
    else
      ui.important("Could not push #{name} to #{remote}: #{out.strip}")
      ui.important("The tag exists locally. Push it with: git push #{remote} #{name}")
    end
  end
end
